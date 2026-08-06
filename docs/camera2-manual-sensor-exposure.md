# Camera2 manual sensor exposure

`Camera2ManualSensorExposureControl` applies a validated shutter time and ISO through CameraX Camera2 interop.

The production adapter disables automatic exposure, sets `SENSOR_EXPOSURE_TIME` and `SENSOR_SENSITIVITY`, and waits for CameraX to confirm the repeating request before capture continues. Cleanup explicitly restores `CONTROL_AE_MODE_ON`.

Requests with non-positive exposure time or ISO are rejected before CameraX is called. Camera cancellation is recoverable; unsupported, rejected, permission, and unexpected failures fail closed through the shared camera-control result contract.

Camera capability gating must verify manual-sensor support and supported shutter/ISO ranges before constructing a request. Physical Pixel 7 Pro validation remains required for every selected rear lens, including bracket consistency, clipping, thermal behavior, and restoration to automatic exposure.
