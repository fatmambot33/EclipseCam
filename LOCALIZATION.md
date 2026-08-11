# Localisation status

EclipseCam is adopting Android string resources as the source of truth for user-facing copy.

## Completed in this milestone

- Camera, Live, Position, and bottom-navigation copy is resource-backed.
- Core readiness, permission, privacy/offline, and solar-safety wording is available in English and French.
- The foreground capture notification channel, status, and actions are translated in English and French.
- The reference eclipse date and countdown format are locale-specific resources rather than hard-coded UI text.
- Gallery phase-aware montage headings, guidance, state, phase-slot labels, and generate/regenerate controls are resource-backed in English and French.
- Instrumentation verifies representative French navigation, safety, permission, countdown, capture-notification, and Gallery montage resources, including formatted montage status text.
- Android lint remains a required CI gate and should reject new hard-coded Android UI strings where supported.

Safety translations intentionally preserve the same conservative requirement as English: certified eye protection is required for direct viewing and an appropriate camera solar filter is required during partial phases.

## Remaining before issue #89 can close

This milestone does not claim full application localisation. Remaining production surfaces, especially the Gallery session browser, timelapse, export/share messages, and lower-level runtime error strings, must move to resources and receive French translations. Representative locale-switch UI/screenshot coverage is also still required to verify long French labels at large font/display scales.

Issue #89 therefore remains open until every production user-facing string and notification is covered and the complete English/French UI matrix passes.
