package com.fatmambo33.eclipsecam.capture

fun interface CaptureServiceRecoveryLoader {
    fun load(): CaptureServiceBootstrapResult
}

fun interface CaptureRuntimeSessionCreator {
    fun create(recovery: CaptureServiceBootstrapResult.Ready): CaptureForegroundRuntimeSession
}

sealed interface CaptureRuntimeHostStartResult {
    data class Ready(val state: CaptureServiceState) : CaptureRuntimeHostStartResult
    data object Missing : CaptureRuntimeHostStartResult
    data class Rejected(val reason: String) : CaptureRuntimeHostStartResult
    data class Failed(val reason: String) : CaptureRuntimeHostStartResult
}

/**
 * Owns exactly one recovered runtime session for an Android foreground-service instance.
 *
 * Startup fails closed when recovery is unavailable, rejected, or concrete session construction
 * fails. Commands never reach a partially constructed runtime. Closing is idempotent and releases
 * the owned session before the service instance can be destroyed.
 */
class CaptureForegroundServiceRuntimeHost(
    private val recoveryLoader: CaptureServiceRecoveryLoader,
    private val sessionCreator: CaptureRuntimeSessionCreator,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var session: CaptureForegroundRuntimeSession? = null

    val state: CaptureServiceState
        get() = synchronized(lock) { session?.state ?: CaptureServiceState.IDLE }

    fun start(): CaptureRuntimeHostStartResult = synchronized(lock) {
        if (closed) return CaptureRuntimeHostStartResult.Failed("Capture runtime host is closed.")
        session?.let { return CaptureRuntimeHostStartResult.Ready(it.state) }

        when (val recovery = recoveryLoader.load()) {
            CaptureServiceBootstrapResult.Missing -> CaptureRuntimeHostStartResult.Missing
            is CaptureServiceBootstrapResult.Rejected ->
                CaptureRuntimeHostStartResult.Rejected(recovery.reason)
            is CaptureServiceBootstrapResult.Ready -> try {
                val created = sessionCreator.create(recovery)
                session = created
                CaptureRuntimeHostStartResult.Ready(created.state)
            } catch (error: RuntimeException) {
                CaptureRuntimeHostStartResult.Failed(
                    error.message ?: "Unable to construct capture runtime session.",
                )
            }
        }
    }

    fun command(command: CaptureServiceCommand): CaptureRuntimeCommandResult? = synchronized(lock) {
        if (closed) return null
        session?.command(command)
    }

    fun tick(): CaptureRuntimeTickResult? = synchronized(lock) {
        if (closed) return null
        session?.tick()
    }

    override fun close() {
        val owned = synchronized(lock) {
            if (closed) return
            closed = true
            session.also { session = null }
        }
        owned?.close()
    }
}
