# Capture runtime health sampling

`AndroidCaptureRuntimeHealthProvider` is the production adapter between Android phone state and the deterministic foreground capture runtime.

Before every capture tick it:

- reads a fresh battery percentage and charging state;
- reads the current Android thermal status;
- reads current app-storage availability;
- evaluates the snapshot with `DeviceHealthPolicy`;
- returns `READY`, `DEGRADED`, or `BLOCKED` without network access or telemetry.

Unknown readings never default to a safe state. They degrade readiness so the runtime can communicate uncertainty. Unsafe battery, thermal, or storage conditions remain blocking decisions.

This repository-side increment does not establish long-session reliability. Screen-off, interruption, recovery, thermal, battery, and storage behavior still require instrumentation and physical Pixel 7 Pro validation before the automatic-capture release gate can pass.
