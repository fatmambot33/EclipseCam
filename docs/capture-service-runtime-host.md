# Capture service runtime host

`CaptureForegroundServiceRuntimeHost` is the fail-closed lifecycle boundary between Android service callbacks and one recovered automatic-capture runtime session.

The host loads durable recovery state, constructs exactly one concrete runtime session, routes commands only after construction succeeds, and closes the owned session idempotently during service destruction. Missing or rejected recovery and CameraX dependency-construction failures leave the host idle; no command can reach a partial or placeholder camera runtime.

This milestone does not claim Android background reliability. `CaptureForegroundService` still needs to instantiate this host with the concrete CameraX session factory, then requires lifecycle instrumentation plus screen-off, interruption, intended-duration, battery, thermal, storage, and Pixel 7 Pro validation.
