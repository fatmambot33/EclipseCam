# Late capture catch-up policy

EclipseCam must not replay an irrecoverable backlog after the process, camera, or device has been suspended.

On each execution tick, the capture engine compares every pending instruction with the configured maximum lateness. Consecutive instructions beyond that tolerance are marked skipped in one atomic checkpoint update. The first instruction exactly on or inside the tolerance remains eligible for normal camera execution on the next tick.

This policy provides three guarantees:

- stale frames never invoke the camera
- recovery catches up to the live eclipse timeline without one persistence write per missed frame
- captured plus skipped counters remain consistent with the durable next-instruction index

A catch-up that consumes the remaining plan completes the session normally. Physical screen-off, process-death, thermal, and long-duration validation on the Pixel 7 Pro remains required before release.