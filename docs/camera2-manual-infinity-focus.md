# Camera2 manual infinity focus

EclipseCam applies manual infinity focus through CameraX Camera2 interop only when the selected camera reports manual-focus support.

The production adapter:

- disables autofocus with `CONTROL_AF_MODE_OFF` before setting lens distance;
- sets `LENS_FOCUS_DISTANCE` to `0.0` diopters, the Camera2 representation of infinity;
- waits for CameraX to confirm the repeating request before capture continues;
- restores `CONTROL_AF_MODE_CONTINUOUS_PICTURE` during sequence cleanup;
- returns the shared recoverable/fatal camera-control result contract;
- propagates coroutine cancellation to the pending CameraX operation.

This repository validation does not establish that every Pixel 7 Pro rear lens accepts or holds the requested focus distance. Physical all-lens testing remains a release gate.
