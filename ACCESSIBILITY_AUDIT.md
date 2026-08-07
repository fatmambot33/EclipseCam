# Accessibility and outdoor-usability audit

This document records repository-verifiable accessibility work for EclipseCam. Physical sunlight readability remains a device-and-environment release check and is not claimed by CI.

## Core-flow findings and fixes

### Semantics

- Bottom navigation exposes one text label per destination; decorative navigation icons are removed from the accessibility tree to avoid duplicate announcements.
- Camera, Live, and Position hero cards expose a textual `stateDescription` in addition to their visible status text and colour.
- Readiness rows expose `Ready` or `Needs attention` state semantics; coloured status dots are decorative only.
- Camera and Position primary actions have stable test tags and visible text labels.
- Gallery session states already include visible textual labels such as Complete, Paused, and Failed rather than relying on colour alone.

### Touch targets and navigation

Material 3 buttons and navigation items retain framework minimum interactive sizing. Emulator instrumentation asserts every bottom-navigation destination is at least 48 dp high and clickable, and the Camera primary action is at least 48 dp high.

The four primary surfaces remain directly reachable from the persistent bottom navigation; no deep navigation is required during eclipse-critical use.

### Large text and display scaling

Camera, Live, and Position content now uses vertical scrolling instead of a fixed non-scrollable column. This prevents lower content and primary actions from becoming permanently unreachable when font or display scaling increases.

Gallery uses `LazyColumn` for session lists and detail content, so its long content is independently scrollable.

### Outdoor contrast

The current UI deliberately uses high-luminance text/status accents on very dark surfaces and always pairs status colour with text. This is a repository design observation, not a physical sunlight-readability result. Contrast and glare must still be checked on the target physical phone at representative outdoor brightness.

## Automated evidence

`CoreAccessibilityInstrumentationTest` verifies representative core-flow semantics, minimum touch-target height, persistent navigation, and status descriptions on the API 35 Pixel 7 emulator profile. Existing Gallery instrumentation covers local session and export controls.

Android lint remains enabled for every pull request and release verification.

## Remaining release evidence

Issue #88 must remain open until the remaining acceptance evidence is complete:

- representative screenshot matrix at enlarged font/display scales;
- explicit light/dark-theme decision and validation for every core surface;
- documented TalkBack focus-order walkthrough of the full arm/capture/export flow once those screens are fully wired; and
- physical Pixel 7 Pro sunlight/glare verification.

No repository test can truthfully substitute for the final physical sunlight check.
