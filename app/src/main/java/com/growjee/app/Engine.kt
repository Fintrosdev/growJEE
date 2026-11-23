package com.growjee.app

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
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

// ───────────────────────── Notifications ─────────────────────────
object Notif {
    const val CH_REMIND = "study_reminders"
    const val CH_TIMER = "focus_timer"
    const val CH_DONE = "focus_done"
    const val CH_SIGNAL = "project_m"
    const val ID_TIMER = 10
    const val ID_DONE = 11
    const val ID_REMIND = 100
    const val ID_SIGNAL = 200

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Nudges to study, based on your goals and revisions" })
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply { description = "Shows the running focus timer" })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Focus finished", NotificationManager.IMPORTANCE_HIGH).apply { description = "Alerts when a focus session or break ends" })
        nm.createNotificationChannel(NotificationChannel(CH_SIGNAL, "Project M signals", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Teaser transmissions" })
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun action(ctx: Context, code: Int, action: String, extra: (Intent.() -> Unit)? = null): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AppReceiver::class.java).setAction(action).apply { extra?.invoke(this) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun build(ctx: Context, channel: String, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(openApp(ctx)).setColor(0xFF75F4E8.toInt())

    fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        if (!canPost(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(id, b.build()) } catch (_: SecurityException) { }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}

// ───────────────────────── Alarms ─────────────────────────
object Alarms {
    const val A_TIMER_END = "com.growjee.app.TIMER_END"
    const val A_START_FOCUS = "com.growjee.app.START_FOCUS"
    const val A_STOP_FOCUS = "com.growjee.app.STOP_FOCUS"
    const val A_REMINDER = "com.growjee.app.REMINDER"
    const val A_SIGNAL = "com.growjee.app.SIGNAL"
    private const val RC_TIMER = 1
    private const val RC_REMINDER = 100   // 100..139
    private const val RC_SIGNAL = 200     // 200..239

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun scheduleTimerEnd(ctx: Context, endMs: Long) {
        am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(endMs, Notif.openApp(ctx)), Notif.action(ctx, RC_TIMER, A_TIMER_END))
    }

    fun cancelTimerEnd(ctx: Context) = am(ctx).cancel(Notif.action(ctx, RC_TIMER, A_TIMER_END))

    private fun setAt(ctx: Context, whenMs: Long, pi: PendingIntent) {
        val a = am(ctx)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !a.canScheduleExactAlarms()) a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            else a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } catch (_: SecurityException) {
            a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    fun nextTrigger(min: Int): Long {
        var t = LocalDate.now().atTime(min / 60, min % 60)
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun scheduleReminder(ctx: Context, index: Int, min: Int) {
        setAt(ctx, nextTrigger(min), Notif.action(ctx, RC_REMINDER + index, A_REMINDER) { putExtra("index", index) })
    }

    /** Cancel everything we own and schedule again from current settings. Safe to call any time. */
    fun rescheduleAll(ctx: Context) {
        val cfg = Cfg.get(ctx)
        for (i in 0 until 40) {
            am(ctx).cancel(Notif.action(ctx, RC_REMINDER + i, A_REMINDER) { putExtra("index", i) })
            am(ctx).cancel(Notif.action(ctx, RC_SIGNAL + i, A_SIGNAL) { putExtra("index", i) })
        }
        if (cfg.remindersOn) cfg.reminders.take(40).forEachIndexed { i, r -> if (r.on) scheduleReminder(ctx, i, r.min) }
        if (cfg.signals) Teasers.list.forEachIndexed { i, t ->
            val at = t.unlocksOn.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at > System.currentTimeMillis()) setAt(ctx, at, Notif.action(ctx, RC_SIGNAL + i, A_SIGNAL) { putExtra("index", i) })
        }
    }
}

// ───────────────────────── Focus timer ─────────────────────────
object Focus {
    fun start(ctx: Context, minutes: Int, subject: String, chapter: String, studyType: String, isBreak: Boolean = false, block: Boolean = true) {
        val cfg = Cfg.get(ctx)
        if (cfg.timerRunning()) return
        val now = System.currentTimeMillis()
        val t = FocusTimerState(now, now + minutes * 60_000L, minutes, subject, chapter, studyType, isBreak, block)
        cfg.setTimer(t)
        Alarms.scheduleTimerEnd(ctx, t.endMs)
        showOngoing(ctx, t)
    }

    fun showOngoing(ctx: Context, t: FocusTimerState) {
        val blocking = !t.isBreak && t.block && Cfg.get(ctx).blockDuringTimer
        val title = if (t.isBreak) "Break" else "Focus · " + t.subject.ifBlank { "Study" }
        val text = (if (t.chapter.isNotBlank()) t.chapter + " · " else "") + if (blocking) "Reels & Shorts blocked" else "Stay on it"
        val b = Notif.build(ctx, Notif.CH_TIMER, title, text)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(t.endMs).setShowWhen(true)
            .addAction(0, "Stop", Notif.action(ctx, 2, Alarms.A_STOP_FOCUS))
        Notif.post(ctx, Notif.ID_TIMER, b)
    }

    /** Stops early. Logs the minutes actually studied (if at least one). */
    fun stop(ctx: Context) {
        val cfg = Cfg.get(ctx)
        val t = cfg.timer ?: return
        Alarms.cancelTimerEnd(ctx)
        Notif.cancel(ctx, Notif.ID_TIMER)
        cfg.setTimer(null)
        val elapsed = ((minOf(System.currentTimeMillis(), t.endMs) - t.startMs) / 60_000L).toInt()
        if (!t.isBreak && elapsed >= 1) {
            log(ctx, t, elapsed)
            Notif.post(ctx, Notif.ID_DONE, Notif.build(ctx, Notif.CH_DONE, "Session saved", "$elapsed min logged to your Daily Study Log.").setAutoCancel(true))
        }
        Bus.tick++
    }

    /** Called when the alarm fires (or on boot/app start if it was missed). */
    fun finish(ctx: Context) {
        val cfg = Cfg.get(ctx)
        val t = cfg.timer ?: return
        Notif.cancel(ctx, Notif.ID_TIMER)
        cfg.setTimer(null)
        if (t.isBreak) {
            Notif.post(ctx, Notif.ID_DONE, Notif.build(ctx, Notif.CH_DONE, "Break over", "Back to it. One more focused block?").setAutoCancel(true).setDefaults(NotificationCompat.DEFAULT_ALL))
        } else {
            log(ctx, t, t.minutes)
            Notif.post(ctx, Notif.ID_DONE, Notif.build(ctx, Notif.CH_DONE, "Focus complete · ${t.minutes} min", "Logged to your Daily Study Log. Take a short break.")
                .setAutoCancel(true).setDefaults(NotificationCompat.DEFAULT_ALL)
                .addAction(0, "5 min break", Notif.action(ctx, 3, Alarms.A_START_FOCUS) { putExtra("break", true); putExtra("minutes", 5) }))
        }
        Bus.tick++
    }

    private fun log(ctx: Context, t: FocusTimerState, minutes: Int) {
        val db = Db.get(ctx)
        db.records("daily").put(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("Date", LocalDate.now().toString())
            .put("Subject", t.subject.ifBlank { "Physics" })
            .put("Chapter", t.chapter)
            .put("Study type", t.studyType)
            .put("Duration (min)", minutes.toString())
            .put("Notes", "Logged by Focus Timer"))
        db.dirty = true
        db.save()
        Sync.hook?.invoke()
    }

    /** If the alarm was missed (reboot, force stop), settle the timer now. */
    fun settleIfExpired(ctx: Context) {
        val t = Cfg.get(ctx).timer ?: return
        if (t.endMs <= System.currentTimeMillis()) finish(ctx) else { Alarms.scheduleTimerEnd(ctx, t.endMs); showOngoing(ctx, t) }
    }
}

// ───────────────────────── Reminder text (built from your real data) ─────────────────────────
object ReminderText {
    /** Returns title to body, or null when the reminder should be skipped. */
    fun build(ctx: Context): Pair<String, String>? {
        val db = Db.get(ctx); val cfg = Cfg.get(ctx)
        val today = LocalDate.now().toString()
        val studied = db.records("daily").objs().filter { it.str("Date") == today }.sumOf { it.num("Duration (min)") }.toInt()
        if (cfg.skipIfDone && studied >= cfg.dailyGoal) return null
        val due = db.records("revisions").objs().count { it.str("Status") != "Completed" && (parseDate(it.str("Due date"))?.let { d -> !d.isAfter(LocalDate.now()) } ?: false) }
        val weak = db.master().objs().filter { it.str("mockMarks") != "" && stars(it.num("mockMarks")) <= 2 }.minByOrNull { it.num("mockMarks") }
        val hour = LocalTime.now().hour
        val title = when { hour < 12 -> "Good morning. Start strong"; hour < 17 -> "Study check-in"; hour < 21 -> "Evening session"; else -> "Last call for today's streak" }
        val parts = mutableListOf<String>()
        parts += if (studied == 0) "Nothing logged today yet." else "$studied of ${cfg.dailyGoal} min done today."
        if (due > 0) parts += "$due revision${if (due == 1) "" else "s"} due."
        weak?.let { parts += "Weakest chapter: ${it.str("chapter")} (${stars(it.num("mockMarks"))}★)." }
        return title to parts.joinToString(" ")
    }
}

// ───────────────────────── Receiver ─────────────────────────
class AppReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notif.ensureChannels(ctx)
        when (intent.action) {
            Alarms.A_TIMER_END -> Focus.finish(ctx)
            Alarms.A_STOP_FOCUS -> Focus.stop(ctx)
            Alarms.A_START_FOCUS -> {
                val brk = intent.getBooleanExtra("break", false)
                Focus.start(ctx, intent.getIntExtra("minutes", 25), if (brk) "Break" else "", "", "Problem Practice", brk, !brk)
                Bus.tick++
            }
            Alarms.A_REMINDER -> {
                val i = intent.getIntExtra("index", -1)
                val cfg = Cfg.get(ctx)
                val r = cfg.reminders.getOrNull(i)
                if (r != null && cfg.remindersOn && r.on) {
                    ReminderText.build(ctx)?.let { (title, body) ->
                        Notif.post(ctx, Notif.ID_REMIND + i, Notif.build(ctx, Notif.CH_REMIND, title, body).setAutoCancel(true)
                            .addAction(0, "Start 25 min", Notif.action(ctx, 4, Alarms.A_START_FOCUS) { putExtra("minutes", 25) }))
                    }
                    Alarms.scheduleReminder(ctx, i, r.min)   // next day
                }
            }
            Alarms.A_SIGNAL -> {
                val i = intent.getIntExtra("index", -1)
                if (Cfg.get(ctx).signals && i in Teasers.list.indices) {
                    Notif.post(ctx, Notif.ID_SIGNAL + i, Notif.build(ctx, Notif.CH_SIGNAL, "▓▒░ SIGNAL RECEIVED", "Project M // ${Teasers.list[i].code} transmission unlocked.").setAutoCancel(true))
                }
            }
        }
    }
}

/** Separate exported receiver for system broadcasts, so AppReceiver can stay private. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Notif.ensureChannels(ctx)
            Alarms.rescheduleAll(ctx)
            Focus.settleIfExpired(ctx)
        }
    }
}
