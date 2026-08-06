# CameraX capture binding

`ProcessCameraProviderCaptureBindingPort` is the production boundary for binding the capture-only `ImageCapture` use case.

The binding request requires the exact camera id selected from the validated capability inventory and a positive JPEG output size. The Camera2-backed selector does not fall back to another lens. Provider lookup, camera availability, and lifecycle binding failures return `Unavailable`, so the automatic-capture graph cannot report success without a real bound camera.

The port owns only its current `ImageCapture` use case and unbinds that use case during replacement or cleanup. The foreground service must still provide a started `LifecycleOwner`, compose the complete `CameraXCaptureControlPort`, and inject the resulting backend factory. Android instrumentation and Pixel 7 Pro rear-lens verification remain required before issue #82 can close.
