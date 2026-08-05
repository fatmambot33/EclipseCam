# Capture runtime loop

`CaptureRuntimeLoop` is the framework-neutral boundary between Android service lifecycle code and the durable capture engine.

It guarantees that:

- start and resume commands request an immediate execution tick;
- pause and stop commands cancel future ticks;
- every tick executes at most one capture-engine step;
- future instructions are scheduled at their exact UTC timestamp;
- completed, failed, inactive, safeguard-paused, and explicitly paused sessions stop scheduling;
- service state and scheduling directives are returned together so Android code does not duplicate transition rules.

This milestone does not attach CameraX or claim screen-off, lifecycle instrumentation, long-session, or physical-device validation. The Android service must apply the returned directives and provide live device-health decisions and a real camera executor before automatic capture is complete.
