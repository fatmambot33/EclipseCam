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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fatmambo33.eclipsecam.MainActivity
import com.fatmambo33.eclipsecam.R

class CaptureForegroundService : Service() {
    private var state = CaptureServiceState.IDLE

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = when (intent?.action) {
            ACTION_PAUSE -> CaptureServiceCommand.PAUSE
            ACTION_STOP -> CaptureServiceCommand.STOP
            else -> CaptureServiceCommand.START
        }

        state = CaptureServiceStateReducer.reduce(state, command)
        when (state) {
            CaptureServiceState.RUNNING,
            CaptureServiceState.PAUSED,
            -> startForeground(NOTIFICATION_ID, buildNotification(state))

            CaptureServiceState.STOPPED -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            CaptureServiceState.IDLE -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
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
        private const val CHANNEL_ID = "eclipse_capture"
        private const val NOTIFICATION_ID = 20260812
        private const val ACTION_START = "com.fatmambo33.eclipsecam.capture.START"
        private const val ACTION_PAUSE = "com.fatmambo33.eclipsecam.capture.PAUSE"
        private const val ACTION_STOP = "com.fatmambo33.eclipsecam.capture.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CaptureForegroundService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, CaptureForegroundService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
