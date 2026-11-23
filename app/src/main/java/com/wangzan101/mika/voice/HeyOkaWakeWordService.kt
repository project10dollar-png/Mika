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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.MainActivity
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * User-opt-in, offline "Hey Oka" phrase listener.
 *
 * Vosk's small English model is downloaded once after the user starts this service.
 * Recognition then runs locally. This is constrained-vocabulary ASR, not a dedicated
 * neural wake-word detector; keep it opt-in and show the persistent microphone notice.
 */
class HeyOkaWakeWordService : Service() {
    @Volatile private var running = false
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
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
        if (running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId)
            return
        }
        createNotificationChannels()
        startForeground(
            ONGOING_NOTIFICATION_ID,
            buildListeningNotification("Menyiapkan pengenalan suara offline…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        running = true
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, true).apply()
        worker = Thread({ listenLoop(startId) }, "HeyOka-Vosk").apply { start() }
    }

    private fun listenLoop(startId: Int) {
        var model: Model? = null
        var recognizer: Recognizer? = null
        try {
            val modelDir = ensureModelDownloaded()
            if (!running) return
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), WAKE_GRAMMAR)
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "AudioRecord buffer tidak didukung" }
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, BUFFER_SIZE * 2)
            )
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Mikrofon tidak dapat diinisialisasi" }
            audio.startRecording()
            updateListeningNotification("Offline aktif — ucapkan “Hey Oka”")
            val buffer = ShortArray(BUFFER_SIZE)
            while (running) {
                val count = audio.read(buffer, 0, buffer.size)
                if (count <= 0) continue
                val complete = recognizer.acceptWaveForm(buffer, count)
                val json = if (complete) recognizer.result else recognizer.partialResult
                if (isWakePhrase(json)) {
                    running = false
                    showWakeWordNotification()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
            }
        } catch (error: Exception) {
            if (running) {
                updateListeningNotification("Gagal menyiapkan model atau mikrofon")
            }
            running = false
            stopSelf(startId)
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            runCatching { recognizer?.close() }
            runCatching { model?.close() }
        }
    }

    private fun isWakePhrase(resultJson: String): Boolean {
        val text = runCatching { JSONObject(resultJson).optString("text") }.getOrDefault("")
        val normalized = text.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
        return normalized == "hey okay"
    }

    /**
     * Downloads the public Apache-2.0 Vosk mobile model once. The archive is never
     * committed to the repository and extraction rejects paths outside the model folder.
     */
    private fun ensureModelDownloaded(): File {
        val target = File(filesDir, MODEL_DIRECTORY)
        if (File(target, "am/final.mdl").isFile && File(target, "conf/mfcc.conf").isFile) return target

        val archive = File(cacheDir, MODEL_ARCHIVE)
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Unduhan model gagal" }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(archive).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        target.deleteRecursively()
        target.mkdirs()
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "")
                if (relative.isNotBlank()) {
                    val output = File(target, relative)
                    val root = target.canonicalFile
                    check(output.canonicalFile.path.startsWith(root.path + File.separator)) {
                        "Arsip model memiliki path tidak aman"
                    }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
        archive.delete()
        check(File(target, "am/final.mdl").isFile) { "Arsip model tidak lengkap" }
        return target
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "HEY OKA voice activation", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Status mikrofon saat aktivasi suara diaktifkan pengguna" }
            )
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "HEY OKA activation", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun buildListeningNotification(message: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HEY OKA siaga")
            .setContentText(message)
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

    private fun updateListeningNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(ONGOING_NOTIFICATION_ID, buildListeningNotification(message))
    }

    private fun showWakeWordNotification() {
        val openApp = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_VOICE_CALL, true)
            .putExtra(EXTRA_WAKE_WORD_DETECTED, true)
        val pending = PendingIntent.getActivity(
            this, 3, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hey Oka")
            .setContentText("Hey Oka terdeteksi. Ketuk untuk memulai percakapan suara.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        getSystemService(NotificationManager::class.java).notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun stopListening() {
        running = false
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply()
        runCatching { recorder?.stop() }
        worker?.interrupt()
        worker = null
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.wangzan101.mika.action.HEY_OKA_WAKE_START"
        const val ACTION_STOP = "com.wangzan101.mika.action.HEY_OKA_WAKE_STOP"
        const val EXTRA_WAKE_WORD_DETECTED = "hey_oka_wake_word_detected"
        private const val PREFS_NAME = "hey_oka_voice"
        private const val PREF_ENABLED = "wake_word_enabled"
        private const val MODEL_DIRECTORY = "vosk-model-small-en-us-0.15"
        private const val MODEL_ARCHIVE = "vosk-model-small-en-us-0.15.zip"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val SAMPLE_RATE = 16_000
        private const val BUFFER_SIZE = 4_096
        private const val WAKE_GRAMMAR = """["hey okay"]"""
        private const val CHANNEL_ID = "hey_oka_wake_word"
        private const val ALERT_CHANNEL_ID = "hey_oka_activation"
        private const val ONGOING_NOTIFICATION_ID = 7101
        private const val WAKE_NOTIFICATION_ID = 7102

        /** Call only from a visible Activity after opt-in and microphone permission grant. */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, HeyOkaWakeWordService::class.java).setAction(ACTION_START)
            )
        }

        /** Stop wake-word listening immediately. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, HeyOkaWakeWordService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
