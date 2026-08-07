# Local export and sharing contract

EclipseCam keeps captures and generated media app-private until the user explicitly chooses an export or share action from a Gallery session.

## No automatic egress

The app does not upload media, select recipients, open a share target, save to the device media library, or create an external document in the background. There is no account requirement in this flow. Every external copy begins with a user tap in **Export & share**.

Interrupted, paused, and failed sessions are not blocked from export: any valid local asset can be selected independently of session completion state.

## JPEG location metadata policy

For every selected asset the user sees a **JPEG location metadata** choice before external publication:

- **Remove** is the default. JPEGs are decoded, their EXIF orientation is applied to the pixels, and the image is re-encoded into a new private staging file without copying EXIF, GPS, XMP, timestamps, maker notes, or other container metadata. If decode or re-encode fails, export fails closed and no external copy is created.
- **Preserve** copies the original asset bytes into private staging unchanged, including any embedded metadata.
- Non-JPEG assets are copied unchanged; the UI states that the location-metadata setting only affects JPEG export.

Original captures and generated outputs are never modified by export staging.

## Private staging first

All flows prepare a complete app-private copy before opening or writing an external destination. Staging uses a unique directory plus a `.partial` file and publishes the staged file only after the copy/sanitizer succeeds and produces a non-empty output.

A staging failure deletes its unique directory. This prevents a failed metadata sanitizer or local copy from exposing a partial asset.

## User-selected document export

**Choose export destination** launches Android's Storage Access Framework with `ACTION_CREATE_DOCUMENT`. The user chooses the destination. EclipseCam then writes the already-complete private staging copy to that URI.

If destination writing fails, EclipseCam attempts to delete the newly created destination document. Cancelling the picker deletes the private staging copy and publishes nothing.

## Device media library

**Save to device library** is available on Android 10+ for image and video assets. EclipseCam inserts a MediaStore item with `IS_PENDING=1`, copies the complete staging artifact, and sets `IS_PENDING=0` only after writing succeeds. Failed writes delete the pending MediaStore item.

Images are saved under `Pictures/EclipseCam`; videos under `Movies/EclipseCam`. No broad storage permission is requested.

## Android share sheet

**Share with Android…** is explicit. EclipseCam stages the selected asset, exposes only `cacheDir/shared-exports/` through a non-exported `FileProvider`, and opens `ACTION_SEND` inside Android's chooser with temporary read permission.

The FileProvider does not expose the capture root or other app-private files. Share staging must remain readable long enough for the chosen recipient to consume it, so completed share staging is retained temporarily and directories older than 24 hours are pruned before later share actions. SAF and MediaStore staging is deleted immediately after completion/cancellation.

## Validation

Repository tests cover:

- fail-closed JPEG staging and original preservation;
- exact-byte preserve behavior;
- cleanup after sanitizer failure;
- supported MIME/media-library classification;
- real Android EXIF GPS removal versus preservation;
- real MediaStore publication and readback;
- FileProvider content URIs and explicit `ACTION_CREATE_DOCUMENT` / `ACTION_SEND` intents;
- Gallery visibility of export/share actions and the default **Remove** privacy selection.

Final privacy-policy publication and Play Console Data Safety declarations remain release-account gates and are tracked separately.
