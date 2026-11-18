package com.voicereminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SpeakService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var text = ""
    private var count = 0
    private val repeats = 5
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var wake: PowerManager.WakeLock? = null
    private var running = true

    // Re-force full alarm volume every 500 ms so the user can't lower it mid-reminder
    private val volumeLock = object : Runnable {
        override fun run() {
            if (!running) return
            forceMax()
            handler.postDelayed(this, 500)
        }
    }

    private fun forceMax() {
        try {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
        } catch (_: SecurityException) { /* DND policy; alarm stream usually still fine */ }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        text = intent?.getStringExtra("text") ?: "your task"
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("remind", "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SpeakService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "remind")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Prepare: $text")
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_ALARM)
            .addAction(Notification.Action.Builder(null, "STOP", stop).build())
            .build()
        startForeground(1, n)

        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voicereminder:wake")
            .apply { acquire(5 * 60 * 1000L) }

        forceMax()
        handler.post(volumeLock)
        tts = TextToSpeech(this, this)
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) { stopSelf(); return }
        t.setLanguage(Locale.getDefault())
        // USAGE_ALARM => plays on the alarm stream, ignores silent/vibrate mode
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { handler.post { stopSelf() } }
            override fun onDone(id: String?) {
                count++
                if (count >= repeats) handler.post { stopSelf() }
                else handler.postDelayed({ speak() }, 3000)
            }
        })
        speak()
    }

    private fun speak() {
        tts?.speak("Reminder. Time to prepare: $text", TextToSpeech.QUEUE_FLUSH, null, "r$count")
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        tts?.stop(); tts?.shutdown()
        if (wake?.isHeld == true) wake?.release()
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
