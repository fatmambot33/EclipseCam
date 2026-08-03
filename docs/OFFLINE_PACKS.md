# Offline eclipse packs

Offline packs are versioned regional archives used by MapLibre when no network is available. Each pack must carry its own attribution, licence, provider, size, creation date, and SHA-256 digest.

## Installation lifecycle

1. Read and validate the signed or trusted manifest.
2. Check available storage against the declared pack size plus safety margin.
3. Download over HTTPS into a temporary file with resumable range requests.
4. Verify the complete file size and SHA-256 digest.
5. Atomically move the verified pack into the installed-pack directory.
6. Retain the manifest beside the installed data for attribution and auditing.
7. Delete both data and manifest when the user removes the pack.

## Provider rule

The provider's written terms must explicitly permit offline storage. Public `tile.openstreetmap.org` tiles are never a valid download source. A production provider has not yet been selected, so this branch intentionally contains no live tile URL or credential.

## Privacy

Pack selection and download state are local. Saved observer positions and eclipse overlays are stored separately from third-party basemap data and are never uploaded by the pack manager.
