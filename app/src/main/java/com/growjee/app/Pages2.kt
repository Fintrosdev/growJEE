package com.growjee.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate

@Composable
fun NotifPermissionCard() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Notif.canPost(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) return
    GlassCard(Modifier.fillMaxWidth()) {
        GText("Notifications are off", 15f, C.Rose, FontWeight.SemiBold)
        GText("Turn them on so timers, reminders and Project M signals can reach you.", 12.5f, C.Muted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
        PrimaryButton("Allow notifications", {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }
}

private fun pickTime(ctx: android.content.Context, minutes: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
}

// ───────────────────────── Focus timer ─────────────────────────
@Composable
fun FocusScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val cfg = Cfg.get(ctx)
    val t = cfg.timer
    val running = t != null && t.endMs > System.currentTimeMillis()
    val now = rememberNow(running)

    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var subject by rememberSaveable { mutableStateOf(SUBJECTS[0]) }
    var chapter by rememberSaveable { mutableStateOf("") }
    var studyType by rememberSaveable { mutableStateOf("Problem Practice") }
    var block by rememberSaveable { mutableStateOf(true) }

    val chapters = remember(vm.tick, subject) { listOf("(none)") + vm.master().objs().filter { it.str("subject") == subject }.map { it.str("chapter") } }
    val todays = remember(vm.tick) { vm.records("daily").objs().filter { parseDate(it.str("Date")) == LocalDate.now() }.reversed() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Focus Timer", "Deep work blocks. Finished sessions log themselves to your Daily Study Log.") }
        item { NotifPermissionCard() }

        if (running && t != null) item {
            val total = (t.endMs - t.startMs).toFloat()
            val left = (t.endMs - now).coerceAtLeast(0)
            val frac = 1f - left / total
            GlassCard(Modifier.fillMaxWidth(), padding = 22.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(250.dp)) {
                        val stroke = Stroke(width = 16f, cap = StrokeCap.Round)
                        val inset = 16f
                        drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = stroke)
                        drawArc(Brush.sweepGradient(listOf(C.Cyan, C.Blue, C.Cyan)), -90f, 360f * frac, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = stroke)
                    }
                    Column2(t, left)
                }
                if (!t.isBreak && t.block && cfg.blockDuringTimer) GText("Reels & Shorts are blocked until the timer ends.", 12.5f, C.Green, modifier = Modifier.padding(top = 14.dp).fillMaxWidth(), align = TextAlign.Center)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    GhostButton(if (t.isBreak) "End break" else "Stop & log time", { Focus.stop(ctx) }, color = C.Rose)
                }
            }
        } else {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    GText("Length", 12f, C.Muted, FontWeight.Bold)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 45, 60, 90).forEach { Chip("$it min", minutes == it) { minutes = it } }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        GhostButton("−5", { minutes = (minutes - 5).coerceAtLeast(5) })
                        GText("$minutes min", 26f, C.Cyan, FontWeight.Bold, Modifier.padding(horizontal = 22.dp))
                        GhostButton("+5", { minutes = (minutes + 5).coerceAtMost(240) })
                    }
                    Box(Modifier.padding(top = 14.dp)) { ChoiceField("Subject", subject, SUBJECTS, { subject = it; chapter = "" }) }
                    Box(Modifier.padding(top = 10.dp)) { ChoiceField("Chapter", chapter.ifBlank { "(none)" }, chapters, { chapter = if (it == "(none)") "" else it }) }
                    Box(Modifier.padding(top = 10.dp)) { ChoiceField("Study type", studyType, choicesOf("studytype")!!, { studyType = it }) }
                    SwitchRow("Block Reels & Shorts", "Lock distractions for the whole session", block && cfg.blockDuringTimer, cfg.blockDuringTimer) { block = it }
                    PrimaryButton("Start focus", { Focus.start(ctx, minutes, subject, chapter, studyType, false, block) }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton("5 min break", { Focus.start(ctx, 5, "Break", "", "", true, false) }, Modifier.weight(1f))
                    GhostButton("10 min break", { Focus.start(ctx, 10, "Break", "", "", true, false) }, Modifier.weight(1f))
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText("Today", 16f, C.Main, FontWeight.SemiBold)
                GText("${todays.sumOf { it.num("Duration (min)") }.toInt()} of ${cfg.dailyGoal} min", 12f, C.Muted)
                Bar(todays.sumOf { it.num("Duration (min)") } / cfg.dailyGoal, C.Cyan, Modifier.padding(top = 10.dp))
                if (todays.isEmpty()) GText("No sessions logged yet today.", 12.5f, C.Dim, modifier = Modifier.padding(top = 10.dp))
                todays.take(8).forEach { ListRow(it.str("Chapter").ifBlank { it.str("Study type", "Study session") }, "${it.str("Subject")} · ${it.str("Study type")}", "${it.str("Duration (min)", "0")} min", C.subject(it.str("Subject"))) }
            }
        }
    }
}

@Composable
private fun Column2(t: FocusTimerState, left: Long) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlitchText(mmss(left), 54f, C.Main, calm = true)
        GText(if (t.isBreak) "BREAK" else t.subject.ifBlank { "FOCUS" }.uppercase(), 12f, C.Cyan, FontWeight.Bold)
        if (t.chapter.isNotBlank()) GText(t.chapter, 12f, C.Muted, maxLines = 1, modifier = Modifier.padding(horizontal = 40.dp), align = TextAlign.Center)
    }
}

// ───────────────────────── Blocker ─────────────────────────
@Composable
fun BlockerScreen() {
    val ctx = LocalContext.current
    val cfg = Cfg.get(ctx)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(ReelsBlockerService.isEnabled(ctx)) }
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) enabled = ReelsBlockerService.isEnabled(ctx) }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    var disclosure by remember { mutableStateOf(false) }
    val t = cfg.timer
    val locked = t != null && t.endMs > System.currentTimeMillis() && !t.isBreak && t.block
    val active = cfg.blockingActive()

    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        containerColor = Color(0xFF121827), titleContentColor = C.Main, textContentColor = C.Muted,
        title = { Text("Allow the blocker to watch for Reels & Shorts?") },
        text = {
            Text(
                "GrowJEE uses Android's Accessibility service only inside Instagram, YouTube, Facebook and TikTok. It checks screen labels to tell when Reels or Shorts are open, then takes you back.\n\n" +
                    "It does not read your messages or posts, and nothing leaves your phone. You can turn it off any time in Android settings.\n\n" +
                    "On the next screen find GrowJEE under Installed apps and switch it on."
            )
        },
        confirmButton = { TextButton({ disclosure = false; ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Continue", color = C.Cyan) } },
        dismissButton = { TextButton({ disclosure = false }) { Text("Not now", color = C.Muted) } }
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Distraction Blocker", "Takes you out of Reels and Shorts during your study hours and focus timers.") }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText(if (!enabled) "Service is off" else if (active) "Blocking right now" else "Ready · waiting for study hours", 17f, if (!enabled) C.Rose else if (active) C.Green else C.Cyan, FontWeight.SemiBold)
                GText("${cfg.blocksToday} blocked today", 12.5f, C.Muted, modifier = Modifier.padding(top = 3.dp))
                if (!enabled) {
                    GText("Android needs you to switch the GrowJEE accessibility service on once.", 12.5f, C.Muted, modifier = Modifier.padding(top = 10.dp))
                    PrimaryButton("Set up blocker", { disclosure = true }, Modifier.fillMaxWidth().padding(top = 12.dp))
                    GText("If Android says the setting is restricted (apps installed from outside the Play Store): open App info, tap ⋮ in the top corner, choose Allow restricted settings, then try again.", 11.5f, C.Dim, modifier = Modifier.padding(top = 12.dp))
                    GhostButton("Open App info", { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Block during study hours", "Uses the windows below, every day", cfg.blockerOn, !locked) { cfg.updateBlockerOn(it) }
                SwitchRow("Also block during focus timers", "Even outside your study hours", cfg.blockDuringTimer, !locked) { cfg.updateBlockDuringTimer(it) }
                if (locked) GText("Locked while a focus timer is running.", 12f, C.Amber, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText("Study hours", 16f, C.Main, FontWeight.SemiBold)
                GText("Tap a time to change it. Overnight ranges work too.", 12f, C.Muted)
                cfg.windows.forEachIndexed { i, w ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GText(fmtMin(w.start), 20f, C.Cyan, FontWeight.Bold, Modifier.clickable(enabled = !locked) { pickTime(ctx, w.start) { v -> cfg.updateWindows(cfg.windows.toMutableList().also { it[i] = w.copy(start = v) }) } })
                        GText("  →  ", 16f, C.Dim)
                        GText(fmtMin(w.end), 20f, C.Cyan, FontWeight.Bold, Modifier.clickable(enabled = !locked) { pickTime(ctx, w.end) { v -> cfg.updateWindows(cfg.windows.toMutableList().also { it[i] = w.copy(end = v) }) } })
                        Box(Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = w.on, enabled = !locked, onCheckedChange = { v -> cfg.updateWindows(cfg.windows.toMutableList().also { it[i] = w.copy(on = v) }) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = C.Ink, checkedTrackColor = C.Cyan, uncheckedThumbColor = C.Muted, uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedBorderColor = Color.Transparent)
                        )
                        GText("✕", 18f, C.Rose, modifier = Modifier.clickable(enabled = !locked) { cfg.updateWindows(cfg.windows.toMutableList().also { it.removeAt(i) }) }.padding(start = 14.dp))
                    }
                }
                GhostButton("＋  Add study hours", { cfg.updateWindows(cfg.windows + Win(6 * 60, 9 * 60)) }, Modifier.fillMaxWidth().padding(top = 14.dp))
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText("What to block", 16f, C.Main, FontWeight.SemiBold)
                ALL_PLATFORMS.forEach { p -> SwitchRow(platformLabel(p), if (p == "facebook") "Best effort. Facebook changes its layout often." else if (p == "tiktok") "Short video only, so the whole app is blocked" else "", p in cfg.platforms, !locked) { cfg.togglePlatform(p, it) } }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText("Good to know", 14.5f, C.Main, FontWeight.SemiBold)
                GText("Detection reads the apps' own screen labels, so an Instagram or YouTube update can break it until the blocker is updated. If Reels or Shorts slip through, switch on the log below and send me the output.", 12f, C.Muted, modifier = Modifier.padding(top = 4.dp))
                SwitchRow("Log screen ids (troubleshooting)", "Writes to Logcat tag GrowJEE-Blocker only", cfg.debugLog, true) { cfg.updateDebugLog(it) }
            }
        }
    }
}

// ───────────────────────── Reminders ─────────────────────────
@Composable
fun RemindersScreen() {
    val ctx = LocalContext.current
    val cfg = Cfg.get(ctx)
    fun resched() = Alarms.rescheduleAll(ctx)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Reminders", "Nudges built from your own data: goal progress, due revisions and your weakest chapter.") }
        item { NotifPermissionCard() }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Study reminders", "Daily nudges at the times below", cfg.remindersOn) { cfg.updateRemindersOn(it); resched() }
                cfg.reminders.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GText(fmtMin(r.min), 22f, if (r.on && cfg.remindersOn) C.Cyan else C.Dim, FontWeight.Bold, Modifier.clickable { pickTime(ctx, r.min) { v -> cfg.updateReminders(cfg.reminders.toMutableList().also { it[i] = r.copy(min = v) }); resched() } })
                        Box(Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = r.on, onCheckedChange = { v -> cfg.updateReminders(cfg.reminders.toMutableList().also { it[i] = r.copy(on = v) }); resched() },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = C.Ink, checkedTrackColor = C.Cyan, uncheckedThumbColor = C.Muted, uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedBorderColor = Color.Transparent)
                        )
                        GText("✕", 18f, C.Rose, modifier = Modifier.clickable { cfg.updateReminders(cfg.reminders.toMutableList().also { it.removeAt(i) }); resched() }.padding(start = 14.dp))
                    }
                }
                GhostButton("＋  Add reminder", { cfg.updateReminders(cfg.reminders + Rem(19 * 60)); resched() }, Modifier.fillMaxWidth().padding(top = 14.dp))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText("Daily goal", 16f, C.Main, FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    GhostButton("−15", { cfg.updateDailyGoal(cfg.dailyGoal - 15) })
                    GText("${cfg.dailyGoal} min", 24f, C.Cyan, FontWeight.Bold, Modifier.padding(horizontal = 20.dp))
                    GhostButton("+15", { cfg.updateDailyGoal(cfg.dailyGoal + 15) })
                }
                SwitchRow("Skip reminders once I've hit my goal", "No nagging after a good day", cfg.skipIfDone) { cfg.updateSkipIfDone(it) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Project M signals", "A cryptic notification each time a new teaser unlocks", cfg.signals) { cfg.updateSignals(it); resched() }
                GhostButton("Send a test notification", {
                    Notif.ensureChannels(ctx)
                    val (title, body) = ReminderText.build(ctx) ?: ("Reminder preview" to "You've already hit today's goal, so real reminders would be skipped now.")
                    Notif.post(ctx, Notif.ID_REMIND + 90, Notif.build(ctx, Notif.CH_REMIND, title, body).setAutoCancel(true))
                }, Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        }
    }
}
