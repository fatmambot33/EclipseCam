# Production camera graph

`ProductionIndexedCameraFactory` is the repository-side composition boundary for recovered automatic capture.

It creates one app-private `CaptureOutputStore` per factory root and composes:

- `CameraCaptureRequestPolicy`
- `CameraCaptureSequencePlanner`
- `CameraCaptureSequenceExecutor`
- `CameraInstructionSequenceExecutor`
- the indexed camera port consumed by the durable checkpoint adapter

The factory requires both a validated selected-camera supplier and a concrete `CameraCaptureSequenceBackendFactory`. It does not install a no-op backend, select an unvalidated fallback lens, or publish partial brackets. Reservation and sequence failures clean up every placeholder output.

Android CameraX binding, control construction, and physical rear-lens verification remain explicit follow-up work under issue #82 and the Pixel 7 Pro release gates.
