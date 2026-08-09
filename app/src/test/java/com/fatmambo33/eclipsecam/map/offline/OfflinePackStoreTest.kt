package com.fatmambo33.eclipsecam.map.offline

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflinePackStoreTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("eclipsecam-offline-pack").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun interruptedPackResumesFromDurableOffsetAndPublishesAfterVerification() {
        val bytes = "verified-offline-map-pack".encodeToByteArray()
        val manifest = manifest(bytes)
        val store = OfflinePackStore(root) { Long.MAX_VALUE }

        val prepared = store.prepare(manifest) as OfflinePackPrepareResult.Ready
        assertEquals(0L, prepared.pack.progress.downloadedBytes)

        val split = 9
        val firstProgress = store.append(manifest, 0L, bytes.copyOfRange(0, split))
        assertEquals(split.toLong(), firstProgress.downloadedBytes)

        val recovered = OfflinePackStore(root) { Long.MAX_VALUE }.prepare(manifest) as OfflinePackPrepareResult.Ready
        assertEquals(OfflinePackState.PAUSED, recovered.pack.state)
        assertEquals(split.toLong(), recovered.pack.progress.downloadedBytes)

        val completeProgress = store.append(manifest, split.toLong(), bytes.copyOfRange(split, bytes.size))
        assertEquals(bytes.size.toLong(), completeProgress.downloadedBytes)

        val result = store.finalize(manifest) as OfflinePackFinalizeResult.Ready
        assertEquals(OfflinePackState.READY, result.pack.state)
        assertTrue(result.pack.readyFile?.isFile == true)
        assertEquals(bytes.toList(), result.pack.readyFile!!.readBytes().toList())
    }

    @Test
    fun nonContiguousChunkIsRejectedWithoutChangingProgress() {
        val bytes = "range-safe-pack".encodeToByteArray()
        val manifest = manifest(bytes)
        val store = OfflinePackStore(root) { Long.MAX_VALUE }
        store.prepare(manifest)
        store.append(manifest, 0L, bytes.copyOfRange(0, 4))

        runCatching { store.append(manifest, 3L, bytes.copyOfRange(4, 7)) }
            .onSuccess { throw AssertionError("Expected non-contiguous range rejection") }

        assertEquals(4L, store.load(manifest.id)?.progress?.downloadedBytes)
    }

    @Test
    fun integrityFailureRemovesCorruptPartialSoItCannotBeResumed() {
        val expected = "expected-pack".encodeToByteArray()
        val manifest = manifest(expected)
        val corrupt = "corrupted!!!".encodeToByteArray()
        assertEquals(expected.size, corrupt.size)
        val store = OfflinePackStore(root) { Long.MAX_VALUE }
        store.prepare(manifest)
        store.append(manifest, 0L, corrupt)

        val result = store.finalize(manifest)
        assertTrue(result is OfflinePackFinalizeResult.IntegrityFailure)
        assertEquals(0L, store.load(manifest.id)?.progress?.downloadedBytes)
        assertNull(store.load(manifest.id)?.readyFile)
    }

    @Test
    fun insufficientSpaceFailsBeforeAnyPackBytesAreWritten() {
        val bytes = ByteArray(128) { it.toByte() }
        val manifest = manifest(bytes)
        val store = OfflinePackStore(root) { 64L }

        val result = store.prepare(manifest)
        assertEquals(OfflinePackPrepareResult.InsufficientStorage(128L, 64L), result)
        assertNull(store.load(manifest.id))
    }

    @Test
    fun deletionRemovesReadyBytesPartialBytesAndMetadata() {
        val bytes = "delete-me-pack".encodeToByteArray()
        val manifest = manifest(bytes)
        val store = OfflinePackStore(root) { Long.MAX_VALUE }
        store.prepare(manifest)
        store.append(manifest, 0L, bytes)
        store.finalize(manifest)
        assertNotNull(store.load(manifest.id))

        assertTrue(store.delete(manifest.id))
        assertNull(store.load(manifest.id))
        assertFalse(File(root, manifest.id).exists())
    }

    @Test
    fun changedVersionOrProviderMetadataCannotSilentlyReuseStoredBytes() {
        val bytes = "versioned-pack".encodeToByteArray()
        val manifest = manifest(bytes)
        val store = OfflinePackStore(root) { Long.MAX_VALUE }
        store.prepare(manifest)
        store.append(manifest, 0L, bytes.copyOfRange(0, 4))

        val changed = manifest.copy(version = manifest.version + 1)
        val result = store.prepare(changed)

        assertTrue(result is OfflinePackPrepareResult.ManifestConflict)
        assertEquals(4L, store.load(manifest.id)?.progress?.downloadedBytes)
    }

    private fun manifest(bytes: ByteArray) = OfflinePackManifest(
        id = "spain-north-2026",
        version = 1,
        regionName = "Northern Spain",
        downloadUrl = "https://offline.example.invalid/spain-north.pack",
        expectedBytes = bytes.size.toLong(),
        sha256 = sha256(bytes),
        createdAtUtc = Instant.parse("2026-08-01T00:00:00Z"),
        attribution = "© OpenStreetMap contributors",
        licenceUrl = "https://www.openstreetmap.org/copyright",
        providerName = "Approved offline test provider",
        providerAllowsOfflineUse = true,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
