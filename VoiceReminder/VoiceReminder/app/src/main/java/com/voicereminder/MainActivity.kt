package com.voicereminder

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var pickBtn: Button
    private var hour = 8
    private var minute = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        input = EditText(this).apply { hint = "Task to prepare (e.g. Take medicine)" }
        pickBtn = Button(this).apply { text = "Time: %02d:%02d (tap to change)".format(hour, minute) }
        val add = Button(this).apply { text = "Add reminder" }
        val test = Button(this).apply { text = "Test voice now" }
        val dnd = Button(this).apply { text = "Allow bypass Do Not Disturb" }
        list = ListView(this)

        listOf(input, pickBtn, add, test, dnd).forEach { root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        pickBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h; minute = m
                pickBtn.text = "Time: %02d:%02d (tap to change)".format(h, m)
            }, hour, minute, true).show()
        }
        add.setOnClickListener {
            val txt = input.text.toString().trim()
            if (txt.isEmpty()) { toast("Enter a task"); return@setOnClickListener }
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            Store.add(this, txt, cal.timeInMillis)
            input.setText(""); refresh(); toast("Reminder set")
        }
        test.setOnClickListener {
            startForegroundService(
                Intent(this, SpeakService::class.java)
                    .putExtra("text", input.text.toString().ifBlank { "test task" })
            )
        }
        dnd.setOnClickListener {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) toast("Already allowed")
            else startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            Store.remove(this, Store.all(this)[pos].id); refresh(); toast("Deleted")
        }
        refresh()
    }

    private fun refresh() {
        val f = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        val items = Store.all(this).map { "${f.format(Date(it.time))}  -  ${it.text}" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
