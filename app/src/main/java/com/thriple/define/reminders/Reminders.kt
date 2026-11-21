package com.thriple.define.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thriple.define.MainActivity
import com.thriple.define.R
import com.thriple.define.data.Store
import com.thriple.define.engine.Body
import com.thriple.define.engine.Muscle
import com.thriple.define.engine.Windows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nudges inside the training windows. One inexact alarm is pending at a time (no exact-alarm permission
 * needed); each firing posts a notification if today's mission isn't done, then schedules the next one.
 */
object Reminders {
    const val CHANNEL = "missions"
    const val ACTION_NUDGE = "com.thriple.define.NUDGE"
    const val ACTION_SNOOZE = "com.thriple.define.SNOOZE"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_OPEN_BODY = "open_body"
    private const val NOTIFICATION_ID = 7
    private const val REQ_NEXT = 1
    private const val REQ_SNOOZED = 2
    private const val REQ_SNOOZE_ACTION = 3
    private const val REQ_OPEN = 4

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Mission reminders", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Nudges inside your morning, lunch and evening windows"
            nm.createNotificationChannel(ch)
        }
    }

    private fun nudgeIntent(context: Context, req: Int, title: String, text: String): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_NUDGE)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(context, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val state = Store.load(context)
        val cancelPi = nudgeIntent(context, REQ_NEXT, "", "")
        am.cancel(cancelPi)
        if (!state.remindersOn) return
        val now = LocalDateTime.now()
        val (day, nudge) = Windows.nextNudge(
            now.toLocalDate().toEpochDay(), now.hour * 60 + now.minute,
            doneOn = { state.doneOn(it) },
            checkDue = { Body.checkInDue(state, it).isNotEmpty() },
        )
        val at = LocalDate.ofEpochDay(day).atStartOfDay().plusMinutes(nudge.minute.toLong())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 3 * 60_000L, nudgeIntent(context, REQ_NEXT, nudge.title, nudge.text))
    }

    fun snooze(context: Context, title: String, text: String, openBody: Boolean) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        val am = context.getSystemService(AlarmManager::class.java)
        am.setWindow(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15 * 60_000L, 2 * 60_000L,
            nudgeIntent(context, REQ_SNOOZED, title, text))
    }

    /**
     * The evening check-in message, merged into the evening reminder: asks about the muscles from the last
     * workout, and also mentions tonight's mission if it isn't done yet.
     */
    fun checkInMessage(muscles: List<Muscle>, missionDone: Boolean): Pair<String, String> {
        val what = Body.list(muscles.take(2)).ifEmpty { "muscles" }
        return if (missionDone) "Quick body check" to "How are your $what feeling? Ten seconds, and tomorrow's mission adapts."
        else "Swim done? Body check first" to "How are your $what? Log it, then tonight's mission adapts to how you feel."
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, title: String, text: String, openBody: Boolean = false) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_BODY, openBody),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context, REQ_SNOOZE_ACTION,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_OPEN_BODY, openBody),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_define)
            .setColor(0xFFFF2E93.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, if (openBody) "Log it" else "Start now", open)
            .addAction(0, "Snooze 15 min", snooze)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(Reminders.EXTRA_TITLE) ?: "Mission waiting"
        val text = intent.getStringExtra(Reminders.EXTRA_TEXT) ?: "Your hero is ready when you are."
        when (intent.action) {
            Reminders.ACTION_NUDGE -> {
                val state = Store.load(context)
                val today = Store.today()
                val now = LocalDateTime.now()
                val minute = now.hour * 60 + now.minute
                val due = if (minute in (Windows.CHECK_IN_MINUTE - 30)..(Windows.CHECK_IN_MINUTE + 40)) Body.checkInDue(state, today) else emptyList()
                if (state.remindersOn) {
                    if (due.isNotEmpty()) {
                        val (t, x) = Reminders.checkInMessage(due, state.doneOn(today))
                        Reminders.show(context, t, x, openBody = true)
                    } else if (!state.doneOn(today)) {
                        Reminders.show(context, title, text)
                    }
                }
                Reminders.scheduleNext(context)
            }
            Reminders.ACTION_SNOOZE -> Reminders.snooze(context, title, text, intent.getBooleanExtra(Reminders.EXTRA_OPEN_BODY, false))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.scheduleNext(context)
    }
}
