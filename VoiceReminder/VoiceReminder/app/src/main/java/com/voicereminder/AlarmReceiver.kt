package com.voicereminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = i.getStringExtra("text") ?: return
        c.startForegroundService(
            Intent(c, SpeakService::class.java).putExtra("text", text)
        )
        Store.remove(c, i.getIntExtra("id", -1))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val now = System.currentTimeMillis()
        Store.all(c).filter { it.time > now }.forEach { Scheduler.schedule(c, it) }
    }
}
