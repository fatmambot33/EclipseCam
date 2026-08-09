# Local capture report

EclipseCam can generate `generated/capture-report.json` for an indexed local capture session.

The report is a deterministic, local-only summary intended to make an eclipse session inspectable without exposing app-private filesystem structure. It records the session status and timestamps, capture and generated-media counts, persisted eclipse-phase counts, ordered capture filenames, sizes, phase labels, instruction indexes, and generated-media filenames/kinds.

## Privacy boundary

The report never adds observer location, GPS, orientation, camera sensor telemetry, absolute filesystem paths, or network identifiers. It only summarizes metadata already present in the local Gallery session index. Media remains local unless the user explicitly exports or shares it through the existing Gallery controls.

## Publication semantics

Generation writes a temporary `capture-report.rendering.json` beside the final report. The existing complete report is replaced only after the new report is fully written. Atomic rename is used when the filesystem supports it, with replace-on-move fallback when atomic moves are unavailable. Failed generation removes the temporary artifact and does not mutate original captures.

Existing capture reports are excluded from `generatedMediaCount` and from the generated-media list so regenerating a report is idempotent and does not make the report describe itself.

## Validation boundary

JVM tests verify deterministic capture ordering, phase ordering, JSON escaping, incomplete/unknown metadata representation, report replacement, temporary-file cleanup, and the absence of absolute session paths. This repository-side validation does not replace Pixel 7 Pro Gallery/playback validation or the release privacy/Data Safety review required by `PRODUCT.md`.
