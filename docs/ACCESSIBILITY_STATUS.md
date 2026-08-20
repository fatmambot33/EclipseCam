# Accessibility status

EclipseCam treats accessibility and outdoor readability as release requirements, not optional polish.

## Automated coverage

Current instrumentation verifies that core bottom navigation remains reachable, primary Camera and Position actions meet a 48 dp minimum touch target, and critical hero status is exposed independently of colour.

Gallery montage generation now adds two explicit accessibility guarantees:

- the generate/regenerate action has a 48 dp minimum touch target;
- montage rendering, completion, and failure status uses a polite accessibility live region so TalkBack can announce asynchronous state changes without interrupting higher-priority speech.

These contracts are covered by `LocalMontageControlsInstrumentationTest`.

## Remaining release work

Issue #88 remains open. Production readiness still requires the complete core-flow focus-order audit, large-font/display-scale screenshot matrix, representative light/dark and French layouts, contrast review, and physical sunlight testing. Automated checks do not replace physical-device or outdoor validation.
