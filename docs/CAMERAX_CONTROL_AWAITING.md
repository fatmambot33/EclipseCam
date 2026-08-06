# CameraX control awaiting contract

CameraX camera-control APIs return asynchronous `ListenableFuture` values. EclipseCam must await those futures before a capture sequence advances so that it never records a JPEG under camera state that has not actually been applied.

`CameraXControlAwaiter` provides the control-port boundary used by the concrete CameraX implementation:

- no Android or capture thread is blocked while a control operation is pending;
- coroutine cancellation cancels the pending CameraX future;
- late completion after cancellation is ignored;
- CameraX operation cancellation is recoverable and pauses the capture transaction;
- invalid requests, permission failures, unsupported operations, and unknown failures are fatal;
- every failure includes the operation name and a stable fallback reason.

The next runtime integration must use this awaiter for binding, focus/metering, exposure compensation, manual sensor controls, and restoration. Physical Pixel 7 Pro validation remains required before those controls can be considered production-ready.
