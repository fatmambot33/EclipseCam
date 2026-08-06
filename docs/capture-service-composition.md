# Capture foreground service composition

`CaptureForegroundServiceSessionFactory` is the fail-closed construction boundary for a recovered automatic-capture session.

It joins the recovered plan and durable checkpoint coordinator to three production dependencies:

- an indexed camera pipeline supplied by the concrete CameraX composition
- fresh local Android device-health evaluation
- a lifecycle-owned replace-all wake-up scheduler

The factory wraps the indexed camera pipeline with `CheckpointIndexedCaptureInstructionExecutor`, ensuring output reservation uses the persisted instruction index and that the instruction still matches the recovered plan before camera or filesystem access.

The factory intentionally has no fallback camera implementation. If the concrete CameraX pipeline cannot be built, service startup must remain paused or stop rather than mark an uncaptured instruction as successful.

Physical service lifecycle, screen-off, interruption, intended-duration, thermal, battery, storage, and Pixel 7 Pro camera validation remain required before the automatic-capture release gate can pass.
