# Localisation status

EclipseCam is adopting Android string resources as the source of truth for user-facing copy.

## Completed in this milestone

- Camera, Live, Position, and bottom-navigation copy is resource-backed.
- Core readiness, permission, privacy/offline, and solar-safety wording is available in English and French.
- Camera preview startup failures now map to stable typed categories before reaching the UI. Missing-camera and preview-start failures use English/French resources; raw CameraX/provider exception messages are not rendered to users.
- The foreground capture notification channel, status, and actions are translated in English and French.
- The reference eclipse date and countdown format are locale-specific resources rather than hard-coded UI text.
- Gallery session browsing, empty/error states, session status, phase summaries, localised date/time display, file-size formatting, and timelapse controls/status are resource-backed in English and French.
- Gallery timelapse runtime failures now map encoder/IO diagnostics to stable presentation categories backed by English/French resources; raw device paths and codec details are not rendered to the user.
- Gallery phase-aware montage headings, guidance, state, phase-slot labels, generate/regenerate controls, and runtime failure states are resource-backed in English and French.
- Unexpected montage renderer/IO diagnostics are no longer rendered directly to the user; they collapse to a stable localised failure category so device paths and codec details cannot leak into UI copy.
- Gallery export/share headings, asset navigation, JPEG location-metadata privacy choices, export destinations, explicit Android sharing, and normal operation status copy are resource-backed in English and French.
- Instrumentation verifies representative French navigation, safety, permission, countdown, capture-notification, Gallery browsing, timelapse, montage, export/share, privacy, and formatted Gallery resources.
- JVM validation enforces exact English/French translatable-string key parity, rejects duplicate or blank translatable strings, and verifies matching Android formatter signatures so a translation cannot silently disappear or drop/change a runtime argument.
- `.github/scripts/verify-localization.py` now checks every XML file in the English and French `values` directories, including duplicate keys across files, and its regression suite runs as a required gate in both Android CI and Release Verification.
- Android lint remains a required CI gate and should reject new hard-coded Android UI strings where supported.

Safety translations intentionally preserve the same conservative requirement as English: certified eye protection is required for direct viewing and an appropriate camera solar filter is required during partial phases.

The Gallery export flow keeps location removal as the privacy-default selection. Localisation changes only presentation; JPEG sanitisation, explicit destination selection, and explicit Android share actions remain unchanged.

## Remaining before issue #89 can close

This milestone does not claim full application localisation. Other lower-level runtime failures, including location-provider errors that can still reach the Position UI, need the same typed/resource-backed presentation audit. Representative locale-switch screenshot coverage is also still required to verify long French labels at large font/display scales and to audit the remaining production surfaces for hard-coded copy.

Issue #89 therefore remains open until every production user-facing string and notification is covered and the complete English/French UI matrix passes.
