package com.wangzan101.mika.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.wangzan101.mika.BuildConfig
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.MainActivity

/**
 * Explicitly opt-in wake-word listener.
 *
 * This service never starts itself at boot. Android requires a visible,
 * user-initiated start for microphone foreground services. After a wake word
 * is detected, microphone capture stops and a notification lets the user open Oka.
 */
class HeyOkaWakeWordService : Service() {
    private var porcupine: PorcupineManager? = null
    private var handledWakeWord = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> startListening(startId)
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startListening(startId: Int) {
        if (porcupine != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId)
            return
        }
        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank() || !assetExists(KEYWORD_ASSET)) {
            stopSelf(startId)
            return
        }

        createNotificationChannels()
        startForeground(
            ONGOING_NOTIFICATION_ID,
            buildListeningNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        try {
            porcupine = PorcupineManager.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                .setKeywordPath(KEYWORD_ASSET)
                .setSensitivity(0.55f)
                .build(this, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        if (handledWakeWord) return
                        handledWakeWord = true
                        stopListening()
                        showWakeWordNotification()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                })
            porcupine?.start()
        } catch (_: Exception) {
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun assetExists(path: String): Boolean =
        runCatching { assets.open(path).close() }.isSuccess

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "HEY OKA voice activation", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Visible status while wake-word detection is enabled" }
            )
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "HEY OKA activation", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun buildListeningNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HEY OKA aktif")
            .setContentText("Mikrofon siaga untuk mendeteksi “Hey Oka”")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_notification,
                "Matikan",
                PendingIntent.getService(
                    this, 2, Intent(this, HeyOkaWakeWordService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun showWakeWordNotification() {
        val openApp = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_WAKE_WORD_DETECTED, true)
        val pending = PendingIntent.getActivity(
            this, 3, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hey Oka")
            .setContentText("Oka mendengarkan. Ketuk untuk membuka asisten.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        getSystemService(NotificationManager::class.java).notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun stopListening() {
        val active = porcupine
        porcupine = null
        if (active != null) {
            runCatching { active.stop() }
            active.delete()
        }
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.wangzan101.mika.action.HEY_OKA_WAKE_START"
        const val ACTION_STOP = "com.wangzan101.mika.action.HEY_OKA_WAKE_STOP"
        const val EXTRA_WAKE_WORD_DETECTED = "hey_oka_wake_word_detected"
        private const val KEYWORD_ASSET = "hey_oka.ppn"
        private const val CHANNEL_ID = "hey_oka_wake_word"
        private const val ALERT_CHANNEL_ID = "hey_oka_activation"
        private const val ONGOING_NOTIFICATION_ID = 7101
        private const val WAKE_NOTIFICATION_ID = 7102

        /** Call only from a visible Activity after opt-in and microphone permission grant. */
        fun start(context: Context) {
            val intent = Intent(context, HeyOkaWakeWordService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        /** Stop wake-word listening immediately. */
        fun stop(context: Context) {
            context.startService(Intent(context, HeyOkaWakeWordService::class.java).setAction(ACTION_STOP))
        }
    }
}
