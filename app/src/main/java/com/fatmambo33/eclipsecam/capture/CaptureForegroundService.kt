package com.fatmambo33.eclipsecam.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.fatmambo33.eclipsecam.MainActivity
import com.fatmambo33.eclipsecam.R
import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilityInventory
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing
import java.io.File

@ExperimentalCamera2Interop
class CaptureForegroundService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var runtimeHost: CaptureForegroundServiceRuntimeHost? = null
    private var commandRouter: CaptureForegroundServiceCommandRouter? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        createNotificationChannel()
        val host = createRuntimeHost()
        runtimeHost = host
        commandRouter = CaptureForegroundServiceCommandRouter(host)
        applyRoute(commandRouter?.initialize() ?: CaptureForegroundServiceRouteResult.Stop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val router = commandRouter ?: run {
            stopCaptureService()
            return START_NOT_STICKY
        }
        val request = when (intent?.action) {
            null -> CaptureForegroundServiceRequest.STICKY_RESTART
            ACTION_PAUSE -> CaptureForegroundServiceRequest.PAUSE
            ACTION_STOP -> CaptureForegroundServiceRequest.STOP
            else -> CaptureForegroundServiceRequest.START
        }
        val route = router.route(request)
        applyRoute(route)
        return if (route is CaptureForegroundServiceRouteResult.Active) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        commandRouter = null
        runtimeHost?.close()
        runtimeHost = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createRuntimeHost(): CaptureForegroundServiceRuntimeHost =
        runtimeHostFactoryOverride?.invoke(this) ?: createProductionRuntimeHost()

    private fun createProductionRuntimeHost(): CaptureForegroundServiceRuntimeHost {
        val recovery = CaptureServiceRecoveryBootstrap.fromFilesDirectory(filesDir)
        val inventory = CameraCapabilityInventory(applicationContext)
        val selectedCamera = {
            inventory.readAll()
                .asSequence()
                .filter { it.facing == LensFacing.BACK }
                .filter { it.jpegSizes.isNotEmpty() }
                .maxByOrNull { camera -> camera.jpegSizes.first().pixelCount }
                ?: error("No compatible rear JPEG camera capability is available.")
        }
        val backendFactory = CameraCaptureSequenceBackendFactory {
            CameraXCaptureSequenceBackend(
                AndroidCameraXCaptureControlPort(
                    context = applicationContext,
                    lifecycleOwner = this,
                ),
            )
        }
        val indexedFactory = ProductionIndexedCameraFactory(
            outputRootDirectory = File(filesDir, "captures"),
            selectedCamera = selectedCamera,
            backendFactory = backendFactory,
        )
        val sessionFactory = CaptureForegroundServiceSessionFactory(
            context = this,
            indexedCameraFactory = indexedFactory,
        )
        return CaptureForegroundServiceRuntimeHost(
            recoveryLoader = CaptureServiceRecoveryLoader(recovery::load),
            sessionCreator = CaptureRuntimeSessionCreator(sessionFactory::create),
        )
    }

    private fun applyRoute(result: CaptureForegroundServiceRouteResult) {
        when (result) {
            is CaptureForegroundServiceRouteResult.Active -> startForeground(
                NOTIFICATION_ID,
                buildNotification(result.state),
            )
            CaptureForegroundServiceRouteResult.Stop -> stopCaptureService()
        }
    }

    private fun stopCaptureService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.capture_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(state: CaptureServiceState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actionIntent = if (state == CaptureServiceState.RUNNING) {
            servicePendingIntent(ACTION_PAUSE, 1)
        } else {
            servicePendingIntent(ACTION_START, 2)
        }
        val actionLabel = if (state == CaptureServiceState.RUNNING) {
            getString(R.string.capture_notification_pause)
        } else {
            getString(R.string.capture_notification_resume)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capture_notification)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(
                if (state == CaptureServiceState.RUNNING) {
                    getString(R.string.capture_notification_running)
                } else {
                    getString(R.string.capture_notification_paused)
                },
            )
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, actionLabel, actionIntent)
            .addAction(
                0,
                getString(R.string.capture_notification_stop),
                servicePendingIntent(ACTION_STOP, 3),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, CaptureForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        internal const val NOTIFICATION_ID = 20260812
        private const val CHANNEL_ID = "eclipse_capture"
        private const val ACTION_START = "com.fatmambo33.eclipsecam.capture.START"
        private const val ACTION_PAUSE = "com.fatmambo33.eclipsecam.capture.PAUSE"
        private const val ACTION_STOP = "com.fatmambo33.eclipsecam.capture.STOP"

        @Volatile
        internal var runtimeHostFactoryOverride:
            ((CaptureForegroundService) -> CaptureForegroundServiceRuntimeHost)? = null

        internal fun clearRuntimeHostFactoryOverride() {
            runtimeHostFactoryOverride = null
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CaptureForegroundService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            context.startService(Intent(context, CaptureForegroundService::class.java).setAction(ACTION_PAUSE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
