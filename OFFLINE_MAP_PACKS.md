# Offline map packs

EclipseCam's offline-pack storage is intentionally separated from tile-provider selection and network transport.

## Durable pack lifecycle

`OfflinePackManifest` records region, version, expected size, SHA-256, attribution, licence URL, provider name, and whether that provider permits offline use. Manifests reject the public `tile.openstreetmap.org` service.

`OfflinePackStore` owns app-private pack bytes and metadata. A downloader prepares a manifest, reads the durable byte offset, requests the next provider-supported range, appends only a contiguous chunk, and finalizes only after the expected byte count is present. Every appended chunk is flushed and synced before progress is reported.

After interruption or process death, the next prepare/load recovers progress from the partial file length. A verified pack is published only after local SHA-256 verification. Corrupt completed bytes are deleted rather than resumed. Manifest/version/provider mismatches fail closed so bytes from one licensed artifact cannot silently be reused as another.

Deletion removes the verified file, partial file, metadata, and pack directory. Preparation checks remaining required bytes against available storage before writing pack bytes.

## Provider-owned boundary

No production download URL, API key, token, provider account, or region catalogue is embedded in the repository. Before the app can expose a real download button, the owner must select an OSM-derived provider or self-hosted package whose terms explicitly allow the intended application and offline distribution/caching. The provider's real manifest data and credential delivery mechanism can then be connected to the transport layer without changing the durable store contract.

## Validation boundary

JVM tests cover interrupted/resumed writes, contiguous range enforcement, SHA-256 publication, corrupt-byte cleanup, low-space rejection, complete deletion, metadata persistence, and version/provider conflict handling.

Remaining release evidence includes a real provider-backed range downloader, user-facing region selection/progress/delete controls, Android interruption/deletion instrumentation, airplane-mode rendering from an installed pack, low-storage behaviour on Android, and physical Pixel 7 Pro verification. Those steps must use the selected provider's actual licence and service contract rather than a speculative endpoint.
