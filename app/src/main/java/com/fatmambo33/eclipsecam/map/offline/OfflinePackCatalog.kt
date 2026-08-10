package com.fatmambo33.eclipsecam.map.offline

/**
 * Provider-approved offline map packs that may be offered to the user.
 *
 * The catalog deliberately contains only validated [OfflinePackManifest] values. It does not
 * discover provider endpoints, mint credentials, or infer licence permission at runtime.
 */
class OfflinePackCatalog(manifests: Collection<OfflinePackManifest>) {
    private val manifestsById: Map<String, OfflinePackManifest>

    init {
        val duplicates = manifests.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Offline pack ids must be unique: ${duplicates.sorted()}" }
        manifestsById = manifests.associateBy { it.id }
    }

    /** Returns provider-approved regions in deterministic display order. */
    fun regions(): List<OfflinePackRegion> = manifestsById.values
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.regionName })
        .map { manifest ->
            OfflinePackRegion(
                id = manifest.id,
                regionName = manifest.regionName,
                estimatedBytes = manifest.expectedBytes,
                providerName = manifest.providerName,
                attribution = manifest.attribution,
            )
        }

    /**
     * Selects one region and evaluates whether a new download can be prepared with the supplied
     * app-private free-space reading.
     *
     * Existing partial-download reuse is intentionally left to [OfflinePackStore.prepare], which
     * owns the durable byte offset and exact low-space calculation.
     */
    fun select(regionId: String, availableBytes: Long): OfflinePackSelectionResult {
        require(availableBytes >= 0) { "availableBytes must be non-negative" }
        val manifest = manifestsById[regionId] ?: return OfflinePackSelectionResult.NotFound(regionId)
        val missingBytes = (manifest.expectedBytes - availableBytes).coerceAtLeast(0)
        return if (missingBytes == 0L) {
            OfflinePackSelectionResult.Ready(manifest)
        } else {
            OfflinePackSelectionResult.InsufficientStorage(
                manifest = manifest,
                availableBytes = availableBytes,
                missingBytes = missingBytes,
            )
        }
    }
}

data class OfflinePackRegion(
    val id: String,
    val regionName: String,
    val estimatedBytes: Long,
    val providerName: String,
    val attribution: String,
)

sealed interface OfflinePackSelectionResult {
    data class Ready(val manifest: OfflinePackManifest) : OfflinePackSelectionResult

    data class InsufficientStorage(
        val manifest: OfflinePackManifest,
        val availableBytes: Long,
        val missingBytes: Long,
    ) : OfflinePackSelectionResult

    data class NotFound(val regionId: String) : OfflinePackSelectionResult
}
