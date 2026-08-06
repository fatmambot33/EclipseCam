# CameraX focus and metering

`CameraXFocusMeteringControl` is the production-side boundary for applying a validated metering point before an automatic capture sequence.

The control starts CameraX autofocus, auto-exposure, and auto-white-balance metering together and waits for CameraX confirmation through the shared cancellation-safe control awaiter. The default point is the centre of the frame. Normalized coordinates outside `0..1` are rejected before CameraX is called.

`restoreContinuousAutoFocus()` cancels the active focus/metering action and waits for CameraX to restore its normal continuous automatic behavior.

This increment does not claim manual infinity focus, white-balance locking, Camera2 manual sensor controls, foreground-service wiring, or physical-device validation. Those remain required by issue #7 and the production release plan.
