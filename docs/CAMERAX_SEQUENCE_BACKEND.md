# CameraX sequence backend contract

The capture sequence backend is asynchronous by design. CameraX preparation, camera-control updates, and JPEG writes complete through callbacks or futures, so `CameraCaptureSequenceBackend` exposes suspending `prepare`, `capture`, and `close` operations.

The executor waits for each operation before advancing to the next exposure frame. It preserves a bracket only after every frame and cleanup step succeeds. Recoverable or fatal failures release all pre-reserved outputs, preventing incomplete brackets from entering the local Gallery index.

Coroutine cancellation is part of the transaction boundary. The executor performs backend close and output release in a non-cancellable context, then rethrows the original cancellation. Cleanup failures are attached as suppressed exceptions rather than converting cancellation into an ordinary camera result.

A concrete CameraX implementation must:

- resume `prepare` only after focus, metering, white-balance, output-size, and camera state are ready;
- resume `capture` only after the JPEG callback confirms the requested destination was written;
- map `ImageCaptureException` through `CameraXCaptureFailureAdapter`;
- cancel pending callbacks without leaving partial files;
- restore temporary camera controls in `close`.

Physical Pixel 7 Pro validation remains required for focus, exposure bracketing, white-balance locking, cancellation, lifecycle recovery, and long-duration capture.
