# CameraX output binding

`AndroidCameraXOutputBinder` owns the atomic boundary between a physical camera id and the JPEG output used by automatic capture.

## Contract

- A non-blank Camera2 camera id and positive output dimensions are required.
- The requested physical camera must be present before any existing use case is unbound.
- Preview is optional; when supplied, preview and JPEG capture are bound to the same lifecycle and camera in one call.
- A binding exception triggers `unbindAll()` so the automatic-capture pipeline cannot remain partially configured.
- The returned `ImageCapture` instance is the one that must be passed to `AndroidCameraXJpegCapturePort`.

## Remaining integration

The foreground service still needs to acquire `ProcessCameraProvider`, create this binder for the service lifecycle, connect the returned camera controls and JPEG port, and unbind on terminal shutdown. Physical validation remains required for every Pixel 7 Pro rear camera and supported output size.
