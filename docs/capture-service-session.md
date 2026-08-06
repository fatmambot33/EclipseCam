# Foreground capture service session ownership

`CaptureForegroundServiceSession` is the lifecycle boundary for one recovered automatic-capture session.

It joins the recovered plan and checkpoint coordinator to the deterministic runtime, fresh device-health sampling, and the replace-all wake-up controller. Android lifecycle commands and scheduled wake-ups are serialized so a pause or stop cannot race a due capture tick.

Calling `close()` invalidates pending callbacks before shutting down the scheduler. A destroyed service instance therefore cannot execute camera work later from a stale queued callback.

This boundary intentionally does not claim physical background-execution reliability. `CaptureForegroundService` still needs its concrete CameraX dependency factory and Android lifecycle instrumentation, followed by screen-off, interruption, and intended-duration testing on the Pixel 7 Pro.
