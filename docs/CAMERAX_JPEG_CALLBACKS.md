# CameraX JPEG callback bridge

`CameraXJpegCapture` is the first concrete CameraX execution slice for automatic capture.

It wraps `ImageCapture.takePicture` behind a testable callback port and suspends until CameraX reports either a completed JPEG write or an `ImageCaptureException`. Callback failures are classified through the shared capture failure policy, so camera closure and transient capture failure remain recoverable while file I/O, invalid-camera, and unknown failures remain fatal.

Coroutine cancellation stops waiting immediately. CameraX does not expose cancellation for an in-flight file capture, so late callbacks are ignored and the surrounding `CameraCaptureSequenceExecutor` remains responsible for non-cancellable backend shutdown and reserved-output cleanup.

This bridge does not yet bind a camera, apply focus or exposure controls, or integrate the foreground runtime loop. Those remain required before issue #7 can be completed.
