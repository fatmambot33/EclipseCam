package com.fatmambo33.eclipsecam.map.offline

import java.security.MessageDigest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePackCatalogTest {
    @Test
    fun regionsAreDeterministicAndExposeEstimatedStorage() {
        val catalog = OfflinePackCatalog(
            listOf(
                manifest(id = "spain-west", regionName = "Spain West", bytes = 2048),
                manifest(id = "france-atlantic", regionName = "France Atlantic", bytes = 1024),
            ),
        )

        val regions = catalog.regions()

        assertEquals(listOf("France Atlantic", "Spain West"), regions.map { it.regionName })
        assertEquals(listOf(1024L, 2048L), regions.map { it.estimatedBytes })
        assertTrue(regions.all { it.attribution.contains("OpenStreetMap") })
    }

    @Test
    fun duplicateManifestIdsAreRejected() {
        val duplicate = manifest(id = "spain-west", regionName = "Spain West", bytes = 1024)

        runCatching { OfflinePackCatalog(listOf(duplicate, duplicate.copy(regionName = "Other"))) }
            .onSuccess { throw AssertionError("Expected duplicate id rejection") }
    }

    @Test
    fun selectionIsReadyWhenEstimatedPackFitsAvailableStorage() {
        val manifest = manifest(id = "spain-west", regionName = "Spain West", bytes = 4096)
        val catalog = OfflinePackCatalog(listOf(manifest))

        val result = catalog.select("spain-west", availableBytes = 4096)

        assertEquals(OfflinePackSelectionResult.Ready(manifest), result)
    }

    @Test
    fun selectionReportsExactMissingStorageBeforeDownload() {
        val manifest = manifest(id = "spain-west", regionName = "Spain West", bytes = 4096)
        val catalog = OfflinePackCatalog(listOf(manifest))

        val result = catalog.select("spain-west", availableBytes = 1024)

        assertEquals(
            OfflinePackSelectionResult.InsufficientStorage(
                manifest = manifest,
                availableBytes = 1024,
                missingBytes = 3072,
            ),
            result,
        )
    }

    @Test
    fun unknownRegionFailsClosedWithoutSelectingAnotherPack() {
        val catalog = OfflinePackCatalog(
            listOf(manifest(id = "spain-west", regionName = "Spain West", bytes = 4096)),
        )

        assertEquals(
            OfflinePackSelectionResult.NotFound("missing"),
            catalog.select("missing", availableBytes = Long.MAX_VALUE),
        )
    }

    @Test
    fun negativeAvailableStorageIsRejected() {
        val catalog = OfflinePackCatalog(
            listOf(manifest(id = "spain-west", regionName = "Spain West", bytes = 4096)),
        )

        runCatching { catalog.select("spain-west", availableBytes = -1) }
            .onSuccess { throw AssertionError("Expected negative storage rejection") }
    }

    private fun manifest(id: String, regionName: String, bytes: Long): OfflinePackManifest {
        val payload = ByteArray(bytes.toInt().coerceAtLeast(1)) { 0x2a }
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        return OfflinePackManifest(
            id = id,
            version = 1,
            regionName = regionName,
            downloadUrl = "https://example.invalid/$id.pack",
            expectedBytes = bytes,
            sha256 = sha256,
            createdAtUtc = Instant.parse("2026-08-10T00:00:00Z"),
            attribution = "© OpenStreetMap contributors",
            licenceUrl = "https://www.openstreetmap.org/copyright",
            providerName = "Test Provider",
            providerAllowsOfflineUse = true,
        )
    }
}
