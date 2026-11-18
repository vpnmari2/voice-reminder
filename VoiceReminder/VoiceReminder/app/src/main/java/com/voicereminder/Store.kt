package com.voicereminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class Task(val id: Int, val text: String, val time: Long)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("tasks", Context.MODE_PRIVATE)

    fun all(c: Context): List<Task> {
        val arr = JSONArray(prefs(c).getString("t", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Task(o.getInt("id"), o.getString("text"), o.getLong("time"))
        }.sortedBy { it.time }
    }

    private fun save(c: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time)) }
        prefs(c).edit().putString("t", arr.toString()).apply()
    }

    fun add(c: Context, text: String, time: Long): Task {
        val t = Task((System.currentTimeMillis() % 1_000_000_000).toInt(), text, time)
        save(c, all(c) + t)
        Scheduler.schedule(c, t)
        return t
    }

    fun remove(c: Context, id: Int) {
        all(c).firstOrNull { it.id == id }?.let { Scheduler.cancel(c, it) }
        save(c, all(c).filter { it.id != id })
    }
}

object Scheduler {
    private fun pi(c: Context, t: Task) = PendingIntent.getBroadcast(
        c, t.id,
        Intent(c, AlarmReceiver::class.java).putExtra("text", t.text).putExtra("id", t.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context, t: Task) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock = exact, works in Doze, needs no extra permission
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t.time, show), pi(c, t))
    }

    fun cancel(c: Context, t: Task) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(c, t))
    }
}
