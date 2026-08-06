# Camera capture pipeline

Automatic capture is intentionally split into deterministic stages:

1. `CameraCaptureRequestPolicy` converts an eclipse-phase instruction and the selected camera capability inventory into a conservative request.
2. `CameraCaptureSequencePlanner` atomically reserves every app-private JPEG required by the exposure program.
3. `CameraInstructionSequenceExecutor` bridges the instruction into the transactional sequence path and fails closed when hardware or local output allocation cannot satisfy the plan.
4. `CameraCaptureSequenceExecutor` applies the backend operations in order, preserves complete brackets, and removes incomplete brackets.
5. `CameraXCaptureSequenceBackend` coordinates CameraX controls and awaits each JPEG callback.

The instruction bridge maps recoverable CameraX failures back to a paused runtime outcome and fatal or unsupported conditions to a failed runtime outcome. It never reports a capture until the complete exposure sequence succeeds.

## Remaining Android integration

The foreground runtime still needs to supply the active session ID, instruction index, selected camera capability snapshot, and concrete CameraX backend. Physical Pixel 7 Pro validation remains mandatory for focus, exposure, white-balance, lifecycle, screen-off, thermal, battery, storage, interruption, and intended-duration behavior.
