# JPEG export privacy

EclipseCam keeps original capture files app-private and does not upload or share them automatically.

`JpegPrivacyExporter` creates a separate local export only after a caller has received an explicit user action. The caller must choose one metadata policy:

- `PRESERVE` copies the original JPEG bytes exactly.
- `REMOVE` removes all pre-scan JPEG APP1 segments. This strips EXIF metadata, including GPS location, without decoding or recompressing the image pixels.

The exporter writes to a temporary sibling file and replaces the destination only after a complete validated operation. Invalid or truncated JPEG input fails closed and leaves no partial destination.

This component does not launch Android sharing. A later Gallery increment must connect successful exports to the Android share sheet only after an explicit user tap and must make the metadata choice visible before export.

## Validation

JVM regression tests cover byte-identical preservation, EXIF removal with scan-data preservation, malformed-input cleanup, and source overwrite prevention.
