# Foreground service lifecycle instrumentation

The Android instrumentation suite now exercises `CaptureForegroundService` through real service and activity lifecycle callbacks while substituting only the recovered runtime host.

## Covered behavior

- service startup promotes the recovered paused session to running after an explicit start command
- the persistent foreground notification is present while the service is active
- pause prevents the runtime session from remaining in the running state
- activity recreation does not close or stop the service-owned session
- a sticky restart callback with a null intent preserves the recovered paused state and does not resume capture
- explicit resume routes a new start command
- explicit stop routes a durable stop command before destruction
- service destruction closes the owned runtime session and invalidates further work

The composition override is internal, empty by default, and used only by instrumentation. Production continues to construct the exact CameraX dependency graph from app-private recovery state.

## Repository validation

Android CI compiles the instrumentation suite with `assembleDebugAndroidTest` while retaining the existing JVM tests, Android lint, and debug application build. Both the app APK and instrumentation APK are uploaded so the exact tested pair can be inspected or executed independently.

Compilation proves that the lifecycle contract remains wired to the real Android service and activity APIs. It does not prove runtime behavior on an emulator or physical phone.

## Remaining validation

Executing the suite on a pinned CI emulator is tracked by issue #92. Screen-off, process-death, intended-duration, battery, thermal, storage, interruption, and all-rear-lens validation on the physical Pixel 7 Pro remain release gates.
