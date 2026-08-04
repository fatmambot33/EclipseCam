package com.fatmambo33.eclipsecam.map.offline

import java.security.MessageDigest
import java.time.Instant

data class OfflinePackManifest(
    val id: String,
    val version: Int,
    val regionName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sha256: String,
    val createdAtUtc: Instant,
    val attribution: String,
    val licenceUrl: String,
    val providerName: String,
    val providerAllowsOfflineUse: Boolean,
) {
    init {
        require(id.matches(Regex("[a-z0-9._-]+")))
        require(version > 0)
        require(regionName.isNotBlank())
        require(downloadUrl.startsWith("https://"))
        require(!downloadUrl.contains("tile.openstreetmap.org", ignoreCase = true))
        require(expectedBytes > 0)
        require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
        require(attribution.contains("OpenStreetMap", ignoreCase = true))
        require(licenceUrl.startsWith("https://"))
        require(providerName.isNotBlank())
        require(providerAllowsOfflineUse)
    }
}

enum class OfflinePackState { NOT_INSTALLED, DOWNLOADING, PAUSED, VERIFYING, READY, FAILED }

data class OfflinePackProgress(val downloadedBytes: Long, val expectedBytes: Long) {
    init {
        require(downloadedBytes >= 0)
        require(expectedBytes > 0)
        require(downloadedBytes <= expectedBytes)
    }

    val fraction: Double get() = downloadedBytes.toDouble() / expectedBytes
}

class OfflinePackIntegrityVerifier {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    fun verify(bytes: ByteArray, manifest: OfflinePackManifest): Boolean =
        sha256(bytes).equals(manifest.sha256, ignoreCase = true)
}
