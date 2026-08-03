package com.fatmambo33.eclipsecam.map.offline

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePackManifestTest {
    private val verifier = OfflinePackIntegrityVerifier()

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPublicOpenStreetMapBulkDownload() {
        manifest(downloadUrl = "https://tile.openstreetmap.org/pack.zip")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProviderWithoutOfflinePermission() {
        manifest(providerAllowsOfflineUse = false)
    }

    @Test
    fun verifiesDownloadedPackIntegrity() {
        val bytes = "offline-pack".toByteArray()
        val pack = manifest(sha256 = verifier.sha256(bytes))
        assertTrue(verifier.verify(bytes, pack))
    }

    @Test
    fun reportsProgressFraction() {
        assertEquals(0.25, OfflinePackProgress(25, 100).fraction, 0.0)
    }

    private fun manifest(
        downloadUrl: String = "https://maps.example.org/iberia-v1.pack",
        sha256: String = "0".repeat(64),
        providerAllowsOfflineUse: Boolean = true,
    ) = OfflinePackManifest(
        id = "iberia-2026",
        version = 1,
        regionName = "Iberian Peninsula",
        downloadUrl = downloadUrl,
        expectedBytes = 100,
        sha256 = sha256,
        createdAtUtc = Instant.parse("2026-08-01T00:00:00Z"),
        attribution = "© OpenStreetMap contributors",
        licenceUrl = "https://www.openstreetmap.org/copyright",
        providerName = "Example provider",
        providerAllowsOfflineUse = providerAllowsOfflineUse,
    )
}
