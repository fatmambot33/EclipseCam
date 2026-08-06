# Foreground capture runtime driver

`CaptureForegroundRuntimeDriver` is the service-side boundary between Android wake-up scheduling and the deterministic capture runtime loop.

The driver:

- converts start and resume commands into an immediate execution tick;
- samples a fresh local device-health decision before every tick;
- replaces the previous wake-up with the runtime's next directive;
- schedules future work at the next capture instant;
- cancels pending work after pause, stop, safeguard pause, failure, completion, or inactivity;
- exposes an explicit shutdown hook for service destruction.

The Android service must provide a wake-up adapter and a fully constructed `CaptureRuntimeLoopPort` backed by the production camera executor. This increment validates the execution and scheduling boundary, but does not claim screen-off, process-death, long-session, or physical Pixel 7 Pro validation.
