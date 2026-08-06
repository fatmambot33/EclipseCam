# Camera2 white-balance lock

`Camera2WhiteBalanceLockControl` applies `CONTROL_AWB_MODE_AUTO` and `CONTROL_AWB_LOCK` through `Camera2CameraControl.addCaptureRequestOptions`.

The operation is suspending and completes only after CameraX reports that the repeating capture result reflects the submitted options. Camera cancellation is recoverable; unsupported, rejected, permission, and unexpected failures fail closed through the shared `CameraXControlResult` contract.

The capture-sequence integration must call `lock()` after metering and before the first JPEG, then call `unlock()` during sequence restoration. Unlocking is explicit and does not clear unrelated Camera2 request options.

## Validation boundary

JVM tests cover lock, unlock, rejected requests, and CameraX cancellation. Physical Pixel 7 Pro validation is still required to confirm that every selected rear lens honors AWB lock without colour drift across a bracket and that restoration resumes automatic white balance.
