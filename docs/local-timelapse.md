# Local timelapse contract

EclipseCam renders timelapses entirely on-device from the original JPEG captures already indexed by the local Gallery.

## Input selection

- Only `LocalSessionAssetKind.ORIGINAL_CAPTURE` JPEG/JPEG assets are candidates.
- Capture filenames written by the production camera store provide the durable UTC capture instant and zero-padded instruction index.
- Frames are ordered by capture instant, then instruction index, then filename as a deterministic tie-break.
- Legacy filenames fall back to the asset's persisted modified timestamp.
- Missing, empty, or JPEGs that fail Android's decode-bounds probe are skipped before export.
- Generated outputs are never fed back into the renderer.

## Video format

The production encoder uses AndroidX Media3 Transformer 1.10.1. Each selected JPEG is represented as a 100 ms image clip at 10 frames per second. The requested output is:

- container: MP4
- video codec: H.264 / AVC (`video/avc`)
- audio: none

Media3 owns image decoding, composition, scaling, and the Android hardware/software encoder choice. Its control API is accessed on the application main looper while export work runs in Media3's background pipeline.

## Progress and cancellation

The Gallery polls Media3 export progress and shows the current percentage. An explicit **Cancel render** action cancels the coroutine and Media3 Transformer operation.

The renderer writes only to `generated/timelapse.rendering.mp4` while work is in progress. Cancellation or failure deletes this temporary file. Original JPEGs are never modified or deleted.

## Publishing and replacement

A completed non-empty temporary MP4 is moved to `generated/timelapse.mp4` with an atomic replace when the filesystem supports it, falling back to a replace move otherwise. An existing complete timelapse remains untouched until the replacement export succeeds.

After publication, the normal `LocalSessionIndex` discovers the MP4 as a `TIMELAPSE` generated asset, so the Gallery refreshes from the same durable local index rather than maintaining a second media database.

## Validation boundary

CI covers deterministic selection, unreadable-frame filtering, progress propagation, cancellation/failure cleanup, original preservation, Android export completion, H.264 track discovery, frame decode, and Gallery rediscovery on the pinned API 35 emulator.

Playback on the target physical Pixel 7 Pro remains an external release gate because emulator codec behavior cannot prove device thermal, hardware-codec, storage, or long-session behavior.
