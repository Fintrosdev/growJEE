package com.growjee.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ───────────────────────── Stats ─────────────────────────
class Stats(
    val mins: Double, val sessions: Int, val attempted: Double, val correct: Double, val mockCount: Int, val mockAvg: Double?,
    val streak: Int, val due: List<JSONObject>, val week: List<Pair<LocalDate, Double>>, val done: Int, val inProgress: Int, val total: Int,
    val subjectProg: List<Triple<String, Int, Int>>, val subjectAcc: List<Triple<String, Double, Double>>,
    val weak: List<JSONObject>, val recentMocks: List<JSONObject>, val todayMin: Double
)

fun computeStats(vm: AppVm): Stats {
    val daily = vm.records("daily").objs(); val questions = vm.records("questions").objs(); val mocks = vm.records("mocks").objs()
    val revisions = vm.records("revisions").objs(); val master = vm.master().objs()
    val today = LocalDate.now()
    val studied = daily.mapNotNull { parseDate(it.str("Date")) }.toSet()
    var streak = 0; var cur = if (studied.contains(today)) today else today.minusDays(1)
    while (studied.contains(cur)) { streak++; cur = cur.minusDays(1) }
    val pcts = mocks.filter { it.num("Total max") > 0 }.map { it.num("Total score") / it.num("Total max") * 100 }
    val due = revisions.filter { it.str("Status") != "Completed" && (parseDate(it.str("Due date"))?.let { d -> !d.isAfter(today) } ?: false) }.sortedBy { it.str("Due date") }
    val week = (6 downTo 0).map { i -> today.minusDays(i.toLong()).let { d -> d to daily.filter { parseDate(it.str("Date")) == d }.sumOf { it.num("Duration (min)") } / 60 } }
    return Stats(
        mins = daily.sumOf { it.num("Duration (min)") }, sessions = daily.size,
        attempted = questions.sumOf { it.num("Attempted") }, correct = questions.sumOf { it.num("Correct") },
        mockCount = mocks.size, mockAvg = if (pcts.isEmpty()) null else pcts.average(), streak = streak, due = due, week = week,
        done = master.count { it.str("status") == "Completed" }, inProgress = master.count { it.str("status") == "In Progress" }, total = master.size,
        subjectProg = SUBJECTS.map { s -> val all = master.filter { it.str("subject") == s }; Triple(s, all.count { it.str("status") == "Completed" }, all.size) },
        subjectAcc = SUBJECTS.map { s -> val qs = questions.filter { it.str("Subject") == s }; Triple(s, qs.sumOf { it.num("Correct") }, qs.sumOf { it.num("Attempted") }) },
        weak = master.filter { it.str("mockMarks") != "" && stars(it.num("mockMarks")) <= 2 }.sortedBy { it.num("mockMarks") }.take(8),
        recentMocks = mocks.sortedByDescending { it.str("Date") }.take(5),
        todayMin = daily.filter { parseDate(it.str("Date")) == today }.sumOf { it.num("Duration (min)") }
    )
}

/** Ticks while [active] so countdowns update. */
@Composable
fun rememberNow(active: Boolean = true): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) { now = System.currentTimeMillis(); delay(500) } }
    return now
}

fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60) }

// ───────────────────────── Dashboard ─────────────────────────
@Composable
fun MetricTile(title: String, value: Double, fmt: (Double) -> String, sub: String, tint: Color, modifier: Modifier = Modifier) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val v by animateFloatAsState(if (go && !value.isNaN()) value.toFloat() else 0f, tween(900, 200, FastOutSlowInEasing), label = "metric")
    GlassCard(modifier, padding = 16.dp) {
        GText(title, 10.5f, C.Muted, FontWeight.Bold)
        GText(if (value.isNaN()) "—" else fmt(v.toDouble()), 28f, tint, FontWeight.Bold, Modifier.padding(top = 4.dp))
        GText(sub, 11f, C.Dim, maxLines = 1)
    }
}

@Composable
fun StudyChart(week: List<Pair<LocalDate, Double>>) {
    val max = maxOf(1.0, week.maxOf { it.second })
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, (d, h) ->
            val today = d == LocalDate.now()
            val frac by animateFloatAsState(if (go && h > 0) maxOf(0.09f, (h / max).toFloat()) else 0f, tween(700, 300 + i * 70, FastOutSlowInEasing), label = "col")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GText(if (h > 0) "%.1f".format(h) else "", 10f, C.Muted)
                Box(Modifier.padding(top = 4.dp).width(26.dp).height(96.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(C.Cyan, Color(0xFF8B95FF)))))
                }
                GText(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), 10.5f, if (today) C.Cyan else C.Muted, if (today) FontWeight.Bold else FontWeight.Normal, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun TeaserPager() {
    val cfg = Cfg.get(LocalContext.current)
    val today = LocalDate.now()
    val list = Teasers.list
    val pager = rememberPagerState(initialPage = maxOf(0, Teasers.unlocked().size - 1)) { list.size }
    val daysLeft = ChronoUnit.DAYS.between(today, Teasers.DROP_DATE)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlitchText("PROJECT M", 15f, C.Cyan, intensity = 0.8f)
            Spacer(Modifier.weight(1f))
            GText(if (daysLeft > 0) "T-MINUS ${daysLeft}D" else "ONLINE", 11.5f, C.Muted, FontWeight.Bold)
        }
        HorizontalPager(pager, contentPadding = PaddingValues(end = 28.dp), pageSpacing = 12.dp) { i ->
            val t = list[i]
            val locked = t.unlocksOn.isAfter(today)
            val fresh = !locked && t.code !in cfg.seenTeasers
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0E1D24), Color(0xFF0B0E1A))))
                    .border(1.dp, if (fresh) C.Cyan else Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .clickable(enabled = fresh) { cfg.markSeen(t.code) }
                    .scanlines()
            ) {
                Column(Modifier.fillMaxHeight().padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GText(t.code, 12f, C.Cyan, FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (fresh) Badge("NEW SIGNAL · tap", C.Cyan)
                    }
                    if (!locked && !fresh) GlitchText(t.text, 17f, C.Main, FontWeight.SemiBold, intensity = 0.6f)
                    else DecodeText(t.text, locked, 17f, if (locked) C.Dim else C.Main)
                    GText(
                        if (locked) "UNLOCKS ${t.unlocksOn.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)).uppercase()} · ${ChronoUnit.DAYS.between(today, t.unlocksOn)}D"
                        else "TRANSMISSION RECEIVED", 10.5f, C.Dim, FontWeight.Bold
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            list.indices.forEach { i ->
                Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (pager.currentPage == i) C.Cyan else Color.White.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
fun FocusMiniCard(onNav: (String) -> Unit, todayMin: Double) {
    val ctx = LocalContext.current
    val cfg = Cfg.get(ctx)
    val t = cfg.timer
    val running = t != null && t.endMs > System.currentTimeMillis()
    val now = rememberNow(running)
    GlassCard(Modifier.fillMaxWidth(), onClick = { onNav("Focus Timer") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GText(if (running) (if (t!!.isBreak) "Break running" else "Focus running") else "Focus timer", 12f, C.Muted, FontWeight.Bold)
                GText(if (running) mmss(t!!.endMs - now) else "${(todayMin).toInt()} min today", 28f, C.Cyan, FontWeight.Bold)
                GText(if (running) (t!!.subject.ifBlank { "Study" } + if (t.chapter.isNotBlank()) " · " + t.chapter else "") else "Goal ${cfg.dailyGoal} min · tap to start a session", 11.5f, C.Dim, maxLines = 1)
            }
            if (!running) PrimaryButton("Start 25", { Focus.start(ctx, 25, "", "", "Problem Practice") })
            else GhostButton("Open", { onNav("Focus Timer") })
        }
        Bar(if (cfg.dailyGoal == 0) 0.0 else todayMin / cfg.dailyGoal, C.Cyan, Modifier.padding(top = 12.dp))
    }
}

@Composable
fun BlockerMiniCard(onNav: (String) -> Unit) {
    val ctx = LocalContext.current
    val cfg = Cfg.get(ctx)
    Bus.tick
    val enabled = ReelsBlockerService.isEnabled(ctx)
    val active = cfg.blockingActive()
    GlassCard(Modifier.fillMaxWidth(), onClick = { onNav("Distraction Blocker") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (!enabled) C.Rose else if (active) C.Green else C.Dim))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                GText("Reels & Shorts blocker", 14.5f, C.Main, FontWeight.SemiBold)
                GText(
                    if (!enabled) "Not enabled yet. Tap to set up." else if (active) "Blocking now · ${cfg.blocksToday} stopped today" else "Idle until your study hours · ${cfg.blocksToday} stopped today",
                    12f, C.Muted
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: AppVm, onNav: (String) -> Unit, onAdd: (String) -> Unit) {
    val s = remember(vm.tick) { computeStats(vm) }
    val hr = LocalTime.now().hour
    val greeting = when { hr < 5 -> "Burning the midnight oil"; hr < 12 -> "Good morning"; hr < 17 -> "Good afternoon"; hr < 22 -> "Good evening"; else -> "Late-night focus" }
    val weekH = s.week.sumOf { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.reveal(0).padding(horizontal = 4.dp)) {
                GlitchText(greeting, 30f, C.Main)
                GText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) + " · only what you log appears here", 12.5f, C.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { Box(Modifier.reveal(1)) { TeaserPager() } }
        item { Box(Modifier.reveal(2)) { FocusMiniCard(onNav, s.todayMin) } }
        item { Box(Modifier.reveal(3)) { BlockerMiniCard(onNav) } }
        item {
            Row(Modifier.reveal(4), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("＋  Study entry", { onAdd("Daily Study Log") }, Modifier.weight(1f))
                GhostButton("＋  Mock test", { onAdd("Mock Test Analytics") }, Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.reveal(5), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("STUDY TIME", s.mins / 60, { "%.1f h".format(it) }, "${s.sessions} sessions", C.Cyan, Modifier.weight(1f))
                    MetricTile("QUESTIONS", s.attempted, { "%,d".format(Math.round(it)) }, "${"%,d".format(s.correct.toLong())} correct", C.Blue, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("ACCURACY", if (s.attempted == 0.0) Double.NaN else Math.round(s.correct / s.attempted * 100).toDouble(), { "${Math.round(it)}%" }, if (s.attempted == 0.0) "No questions yet" else "all subjects", C.Green, Modifier.weight(1f))
                    MetricTile("MOCK TESTS", s.mockCount.toDouble(), { "${Math.round(it)}" }, s.mockAvg?.let { "avg ${Math.round(it)}%" } ?: "None yet", C.Chem, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("STREAK", s.streak.toDouble(), { "${Math.round(it)} d" }, if (s.streak == 0) "Log today to start" else "days in a row", C.Amber, Modifier.weight(1f))
                    MetricTile("REVISIONS DUE", s.due.size.toDouble(), { "${Math.round(it)}" }, if (s.due.isEmpty()) "All caught up" else "up to today", if (s.due.isEmpty()) C.Green else C.Rose, Modifier.weight(1f))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().reveal(6)) {
                GText("Study rhythm · last 7 days", 16f, C.Main, FontWeight.SemiBold)
                GText("Hours per day · ${"%.1f".format(weekH)} h this week", 11.5f, C.Muted)
                StudyChart(s.week)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().reveal(7)) {
                GText("Chapter progress", 16f, C.Main, FontWeight.SemiBold)
                GText("${s.done} of ${s.total} completed · ${s.inProgress} in progress", 11.5f, C.Muted)
                Bar(if (s.total == 0) 0.0 else s.done.toDouble() / s.total, C.Cyan, Modifier.padding(top = 12.dp), 10.dp)
                s.subjectProg.forEachIndexed { i, (sub, d, t) -> ProgressRow(sub, "$d / $t", if (t == 0) 0.0 else d.toDouble() / t, C.subject(sub), (i + 1) * 120) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().reveal(8)) {
                GText("Accuracy by subject", 16f, C.Main, FontWeight.SemiBold)
                s.subjectAcc.forEachIndexed { i, (sub, c, a) ->
                    ProgressRow(sub, if (a == 0.0) "No entries yet" else "${Math.round(c / a * 100)}% · ${c.toInt()}/${a.toInt()}", if (a == 0.0) 0.0 else c / a, C.subject(sub), (i + 1) * 120)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().reveal(9)) {
                GText("Focus chapters", 16f, C.Main, FontWeight.SemiBold)
                GText("Lowest entered marks", 11.5f, C.Muted)
                if (s.weak.isEmpty()) GText("No scored chapters yet. Add marks in Chapter Master.", 12.5f, C.Dim, modifier = Modifier.padding(top = 10.dp))
                s.weak.forEach { ListRow(it.str("chapter"), "${it.str("subject")} · ${it.num("mockMarks").toInt()}/100", starText(it.num("mockMarks")), C.subject(it.str("subject")), C.Gold) }
            }
        }
        if (s.due.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth().reveal(10)) {
                GText("Revisions due", 16f, C.Main, FontWeight.SemiBold)
                s.due.take(6).forEach { ListRow(it.str("Chapter", "Revision"), "${it.str("Subject")} · revision ${it.str("Revision number", "—")}", it.str("Due date"), C.subject(it.str("Subject")), C.Rose) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().reveal(11)) {
                GText("Recent mock tests", 16f, C.Main, FontWeight.SemiBold)
                if (s.recentMocks.isEmpty()) GText("No mock tests yet.", 12.5f, C.Dim, modifier = Modifier.padding(top = 10.dp))
                s.recentMocks.forEach {
                    val mx = it.num("Total max")
                    ListRow(it.str("Test name", "Mock test"), "${it.str("Date")} · ${it.str("Exam")}", "${it.str("Total score", "—")} / ${it.str("Total max", "—")}", C.Cyan, if (mx > 0) C.pct(it.num("Total score") / mx * 100) else C.Muted)
                }
            }
        }
            item {
            GlassCard(Modifier.fillMaxWidth().reveal(12)) {
                GText("PROJECT M // SOURCE INTEGRITY", 10f, C.Rose, FontWeight.Bold)
                GText("MESSING WITH SOURCE CODE TO END IN SEVERE REPRUCISSIONS", 12.5f, C.Main, FontWeight.SemiBold, Modifier.padding(top = 7.dp))
                GText("A dramatic Project M teaser.", 11f, C.Muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GText("© 2026 FintrosDev · The Basement · Darshrey Media. All rights reserved.", 10f, C.Dim)
            }
        }
}
}

// ───────────────────────── List pages ─────────────────────────
@Composable
fun ItemCard(
    title: String, detail: String, accent: Color, badge: String = "", badgeColor: Color = C.Muted,
    onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    GlassCard(modifier.fillMaxWidth(), onClick = onOpen, padding = 14.dp) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().width(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                GText(title, 15f, C.Main, FontWeight.SemiBold, maxLines = 1)
                GText(detail, 12f, C.Muted, modifier = Modifier.padding(top = 3.dp))
            }
            Badge(badge, badgeColor)
            Box {
                GText("⋮", 20f, C.Muted, modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 4.dp))
                DropdownMenu(menu, { menu = false }, containerColor = Color(0xF2161C2B)) {
                    DropdownMenuItem(text = { Text("Edit / details", color = C.Main) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Delete", color = C.Rose) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun PageHeader(title: String, detail: String) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        GlitchText(title, 28f, C.Main, calm = true)
        GText(detail, 12.5f, C.Muted, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun ChapterScreen(vm: AppVm, onEdit: (JSONObject?) -> Unit, onDelete: (JSONObject) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    val all = remember(vm.tick) { vm.master().objs() }
    val shown = remember(vm.tick, filter, search) {
        all.filter { (filter == "All" || it.str("subject") == filter) && (search.isBlank() || it.str("chapter").contains(search.trim(), ignoreCase = true)) }
            .sortedBy { if (it.str("mockMarks") == "") 101.0 else it.num("mockMarks") }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader("Chapter Master", "Enter actual mock marks. Weak chapters sort to the top.") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All") + SUBJECTS) { f -> Chip("$f · ${if (f == "All") all.size else all.count { it.str("subject") == f }}", filter == f) { filter = f } }
            }
        }
        item {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search chapters") }, colors = fieldColors(), shape = RoundedCornerShape(14.dp))
        }
        item { GText("${shown.size} chapter${if (shown.size == 1) "" else "s"}", 11.5f, C.Dim, modifier = Modifier.padding(start = 4.dp)) }
        items(shown, key = { it.str("id") }) { c ->
            val st = c.str("status", "Not Started")
            val marks = c.str("mockMarks")
            ItemCard(
                c.str("chapter"), "${c.str("subject")} · " + if (marks == "") "Not rated" else "${c.num("mockMarks").toInt()}/100 · ${starText(c.num("mockMarks"))}",
                C.subject(c.str("subject")), st, if (st == "Completed") C.Green else if (st == "In Progress") C.Amber else C.Dim,
                onOpen = { onEdit(c) }, onDelete = { onDelete(c) }
            )
        }
    }
}

fun recordTitle(p: String, x: JSONObject) = when (p) {
    "Daily Study Log" -> x.str("Chapter").ifBlank { x.str("Study type", "Study session") }
    "Question Bank Log" -> x.str("Chapter").ifBlank { x.str("Source / set", "Question practice") }
    "Mock Test Analytics" -> x.str("Test name", "Mock test")
    "Mistake Register" -> x.str("Chapter", "Mistake")
    "Revision Scheduler" -> x.str("Chapter", "Revision")
    else -> x.str("Target type", "Weekly target")
}

fun recordSubtitle(vm: AppVm, p: String, x: JSONObject): String {
    val date = x.str("Date").ifBlank { x.str("Due date").ifBlank { x.str("Week starting") } }
    return when (p) {
        "Mock Test Analytics" -> "$date · ${x.str("Exam")} · ${x.str("Total score", "—")} / ${x.str("Total max", "—")} · ${vm.linkedCount(x.str("id"))} linked errors"
        "Mistake Register" -> "$date · ${x.str("Subject")} · ${x.str("Mistake type")} · ${vm.mockName(x.str("Mock test"))}"
        "Daily Study Log" -> "$date · ${x.str("Subject")} · ${x.str("Duration (min)", "0")} min · ${x.str("Study type")}"
        "Question Bank Log" -> "$date · ${x.str("Subject")} · ${x.str("Correct", "0")} / ${x.str("Attempted", "0")} correct"
        else -> "$date · ${x.str("Subject")} · ${x.str("Status", x.str("Revision number"))}"
    }
}

fun recordBadge(p: String, x: JSONObject): Pair<String, Color> = when (p) {
    "Daily Study Log" -> "${x.str("Duration (min)", "0")} min" to C.Cyan
    "Question Bank Log" -> x.num("Attempted").let { a -> if (a > 0) "${Math.round(x.num("Correct") / a * 100)}%" to C.pct(x.num("Correct") / a * 100) else "" to C.Muted }
    "Mock Test Analytics" -> x.num("Total max").let { t -> if (t > 0) "${Math.round(x.num("Total score") / t * 100)}%" to C.pct(x.num("Total score") / t * 100) else "" to C.Muted }
    "Mistake Register" -> if (x.str("Resolved?") == "Yes") "Resolved" to C.Green else x.str("Mistake type") to C.Amber
    "Revision Scheduler" -> x.str("Status").let { it to if (it == "Completed") C.Green else if (it == "In Progress") C.Amber else C.Blue }
    else -> x.num("Target value").let { t -> if (t > 0) "${Math.round(x.num("Actual value") / t * 100)}%" to C.pct(x.num("Actual value") / t * 100) else "" to C.Muted }
}

@Composable
fun RecordsScreen(vm: AppVm, page: String, onEdit: (JSONObject) -> Unit, onMock: (JSONObject) -> Unit, onDelete: (String, JSONObject) -> Unit) {
    val key = keyOf(page)
    val records = remember(vm.tick, page) { vm.records(key).objs().reversed() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(page, describe(page)) }
        if (records.isEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                GText("Nothing logged yet", 15f, C.Main, FontWeight.SemiBold)
                GText("Tap + to add your first entry. Your records sync to your account when you sign in.", 12.5f, C.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(records, key = { it.str("id") }) { r ->
            val (badge, bc) = recordBadge(page, r)
            ItemCard(
                recordTitle(page, r), recordSubtitle(vm, page, r), if (page == "Mock Test Analytics") C.Cyan else C.subject(r.str("Subject")), badge, bc,
                onOpen = { if (page == "Mock Test Analytics") onMock(r) else onEdit(r) }, onDelete = { onDelete(key, r) }
            )
        }
    }
}
