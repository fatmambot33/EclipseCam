package com.fatmambo33.eclipsecam.map.offline

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

/** Durable local state for one regional offline map pack. */
data class StoredOfflinePack(
    val manifest: OfflinePackManifest,
    val state: OfflinePackState,
    val progress: OfflinePackProgress,
    val readyFile: File?,
)

sealed interface OfflinePackPrepareResult {
    data class Ready(val pack: StoredOfflinePack) : OfflinePackPrepareResult
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : OfflinePackPrepareResult
    data class ManifestConflict(val reason: String) : OfflinePackPrepareResult
}

sealed interface OfflinePackFinalizeResult {
    data class Ready(val pack: StoredOfflinePack) : OfflinePackFinalizeResult
    data class Incomplete(val progress: OfflinePackProgress) : OfflinePackFinalizeResult
    data class IntegrityFailure(val expectedSha256: String, val actualSha256: String) : OfflinePackFinalizeResult
}

/**
 * App-private, interruption-safe storage for provider-approved offline pack bytes.
 *
 * Network transport is deliberately outside this class. A downloader must call
 * [prepare], request bytes starting at the returned progress offset, append each
 * contiguous chunk with [append], and call [finalize] only after all bytes arrive.
 */
class OfflinePackStore(
    private val rootDirectory: File,
    private val availableBytes: (File) -> Long = File::getUsableSpace,
) {
    fun prepare(manifest: OfflinePackManifest): OfflinePackPrepareResult {
        val directory = packDirectory(manifest.id)
        directory.mkdirs()

        val storedManifest = readManifest(manifest.id)
        if (storedManifest != null && !storedManifest.sameArtifactAs(manifest)) {
            return OfflinePackPrepareResult.ManifestConflict(
                "Stored pack metadata does not match ${manifest.id} version ${manifest.version}",
            )
        }

        val ready = readyFile(manifest.id)
        if (ready.exists()) {
            if (ready.length() != manifest.expectedBytes) {
                ready.delete()
            } else {
                val actual = sha256(ready)
                if (actual.equals(manifest.sha256, ignoreCase = true)) {
                    writeManifest(manifest)
                    return OfflinePackPrepareResult.Ready(
                        StoredOfflinePack(
                            manifest = manifest,
                            state = OfflinePackState.READY,
                            progress = OfflinePackProgress(manifest.expectedBytes, manifest.expectedBytes),
                            readyFile = ready,
                        ),
                    )
                }
                ready.delete()
            }
        }

        val partial = partialFile(manifest.id)
        if (partial.length() > manifest.expectedBytes) partial.delete()
        val downloaded = partial.length()
        val remaining = manifest.expectedBytes - downloaded
        val usable = availableBytes(directory).coerceAtLeast(0L)
        if (remaining > usable) {
            return OfflinePackPrepareResult.InsufficientStorage(remaining, usable)
        }

        writeManifest(manifest)
        return OfflinePackPrepareResult.Ready(
            StoredOfflinePack(
                manifest = manifest,
                state = if (downloaded == 0L) OfflinePackState.NOT_INSTALLED else OfflinePackState.PAUSED,
                progress = OfflinePackProgress(downloaded, manifest.expectedBytes),
                readyFile = null,
            ),
        )
    }

    /** Append exactly the next contiguous range and force it to durable storage. */
    fun append(manifest: OfflinePackManifest, offset: Long, bytes: ByteArray): OfflinePackProgress {
        require(bytes.isNotEmpty()) { "Offline pack chunks must not be empty" }
        require(offset >= 0L) { "Offset must not be negative" }
        require(readManifest(manifest.id)?.sameArtifactAs(manifest) == true) {
            "Offline pack must be prepared with matching metadata before append"
        }

        val partial = partialFile(manifest.id)
        require(partial.length() == offset) {
            "Chunk offset $offset does not match durable progress ${partial.length()}"
        }
        require(offset + bytes.size <= manifest.expectedBytes) { "Chunk exceeds expected pack size" }

        FileOutputStream(partial, true).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        return OfflinePackProgress(partial.length(), manifest.expectedBytes)
    }

    /** Verify size and SHA-256 before atomically publishing a completed pack. */
    fun finalize(manifest: OfflinePackManifest): OfflinePackFinalizeResult {
        require(readManifest(manifest.id)?.sameArtifactAs(manifest) == true) {
            "Offline pack must be prepared with matching metadata before finalization"
        }
        val partial = partialFile(manifest.id)
        val progress = OfflinePackProgress(partial.length().coerceAtMost(manifest.expectedBytes), manifest.expectedBytes)
        if (partial.length() != manifest.expectedBytes) return OfflinePackFinalizeResult.Incomplete(progress)

        val actualSha256 = sha256(partial)
        if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
            partial.delete()
            return OfflinePackFinalizeResult.IntegrityFailure(manifest.sha256.lowercase(), actualSha256)
        }

        val ready = readyFile(manifest.id)
        if (ready.exists()) ready.delete()
        check(partial.renameTo(ready)) { "Unable to publish verified offline pack atomically" }
        writeManifest(manifest)
        return OfflinePackFinalizeResult.Ready(
            StoredOfflinePack(
                manifest = manifest,
                state = OfflinePackState.READY,
                progress = OfflinePackProgress(manifest.expectedBytes, manifest.expectedBytes),
                readyFile = ready,
            ),
        )
    }

    /** Recover progress after process death or app restart. */
    fun load(packId: String): StoredOfflinePack? {
        val manifest = readManifest(packId) ?: return null
        val ready = readyFile(packId)
        if (ready.exists() && ready.length() == manifest.expectedBytes &&
            sha256(ready).equals(manifest.sha256, ignoreCase = true)
        ) {
            return StoredOfflinePack(
                manifest,
                OfflinePackState.READY,
                OfflinePackProgress(manifest.expectedBytes, manifest.expectedBytes),
                ready,
            )
        }

        val partial = partialFile(packId)
        if (partial.length() > manifest.expectedBytes) partial.delete()
        val downloaded = partial.length()
        return StoredOfflinePack(
            manifest = manifest,
            state = if (downloaded == 0L) OfflinePackState.NOT_INSTALLED else OfflinePackState.PAUSED,
            progress = OfflinePackProgress(downloaded, manifest.expectedBytes),
            readyFile = null,
        )
    }

    /** Remove verified bytes, partial bytes, metadata, and the empty pack directory. */
    fun delete(packId: String): Boolean {
        val directory = packDirectory(packId)
        if (!directory.exists()) return true
        directory.deleteRecursively()
        return !directory.exists()
    }

    private fun packDirectory(packId: String): File {
        require(packId.matches(Regex("[a-z0-9._-]+"))) { "Invalid pack id" }
        return File(rootDirectory, packId)
    }

    private fun partialFile(packId: String) = File(packDirectory(packId), "pack.partial")
    private fun readyFile(packId: String) = File(packDirectory(packId), "pack.ready")
    private fun manifestFile(packId: String) = File(packDirectory(packId), "manifest.properties")

    private fun writeManifest(manifest: OfflinePackManifest) {
        val directory = packDirectory(manifest.id).apply { mkdirs() }
        val target = manifestFile(manifest.id)
        val temporary = File(directory, "manifest.properties.tmp")
        FileOutputStream(temporary).use { output ->
            manifest.toProperties().store(output, "EclipseCam offline pack metadata")
            output.flush()
            output.fd.sync()
        }
        if (target.exists()) check(target.delete()) { "Unable to replace offline pack metadata" }
        check(temporary.renameTo(target)) { "Unable to publish offline pack metadata atomically" }
    }

    private fun readManifest(packId: String): OfflinePackManifest? {
        val file = manifestFile(packId)
        if (!file.isFile) return null
        return runCatching {
            FileInputStream(file).use { input ->
                Properties().apply { load(input) }.toManifest()
            }
        }.getOrNull()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private fun OfflinePackManifest.sameArtifactAs(other: OfflinePackManifest): Boolean =
    id == other.id &&
        version == other.version &&
        expectedBytes == other.expectedBytes &&
        sha256.equals(other.sha256, ignoreCase = true) &&
        downloadUrl == other.downloadUrl &&
        providerName == other.providerName &&
        providerAllowsOfflineUse == other.providerAllowsOfflineUse &&
        attribution == other.attribution &&
        licenceUrl == other.licenceUrl

private fun OfflinePackManifest.toProperties() = Properties().apply {
    setProperty("id", id)
    setProperty("version", version.toString())
    setProperty("regionName", regionName)
    setProperty("downloadUrl", downloadUrl)
    setProperty("expectedBytes", expectedBytes.toString())
    setProperty("sha256", sha256.lowercase())
    setProperty("createdAtUtc", createdAtUtc.toString())
    setProperty("attribution", attribution)
    setProperty("licenceUrl", licenceUrl)
    setProperty("providerName", providerName)
    setProperty("providerAllowsOfflineUse", providerAllowsOfflineUse.toString())
}

private fun Properties.toManifest() = OfflinePackManifest(
    id = requireProperty("id"),
    version = requireProperty("version").toInt(),
    regionName = requireProperty("regionName"),
    downloadUrl = requireProperty("downloadUrl"),
    expectedBytes = requireProperty("expectedBytes").toLong(),
    sha256 = requireProperty("sha256"),
    createdAtUtc = Instant.parse(requireProperty("createdAtUtc")),
    attribution = requireProperty("attribution"),
    licenceUrl = requireProperty("licenceUrl"),
    providerName = requireProperty("providerName"),
    providerAllowsOfflineUse = requireProperty("providerAllowsOfflineUse").toBooleanStrict(),
)

private fun Properties.requireProperty(name: String): String =
    getProperty(name)?.takeIf(String::isNotBlank) ?: error("Missing offline pack metadata: $name")
