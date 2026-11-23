package com.growjee.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Global "something changed" counter. Screens read it so they refresh after the timer or a receiver edits data. */
object Bus {
    var tick by mutableIntStateOf(0)
}

/** Lets background code ask the running ViewModel (if any) to push to the cloud. */
object Sync {
    var hook: (() -> Unit)? = null
}

data class Win(val start: Int, val end: Int, val on: Boolean = true)           // minutes since midnight
data class Rem(val min: Int, val on: Boolean = true)
data class FocusTimerState(
    val startMs: Long, val endMs: Long, val minutes: Int,
    val subject: String, val chapter: String, val studyType: String,
    val isBreak: Boolean, val block: Boolean
)

val ALL_PLATFORMS = listOf("instagram", "youtube", "facebook", "tiktok")
fun platformLabel(p: String) = when (p) {
    "instagram" -> "Instagram Reels"; "youtube" -> "YouTube Shorts"; "facebook" -> "Facebook Reels"; "tiktok" -> "TikTok (whole app)"; else -> p
}

fun fmtMin(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

/** All app settings. Observable from Compose and readable from services/receivers (same process). */
class Cfg private constructor(ctx: Context) {
    private val sp = ctx.getSharedPreferences("growjee_cfg", Context.MODE_PRIVATE)

    // ───── Focus timer ─────
    var timer by mutableStateOf(readTimer()); private set
    fun updateTimer(t: FocusTimerState?) {
        timer = t
        sp.edit().putString("timer", t?.let {
            JSONObject().put("s", it.startMs).put("e", it.endMs).put("m", it.minutes).put("sub", it.subject).put("ch", it.chapter)
                .put("st", it.studyType).put("b", it.isBreak).put("bl", it.block).toString()
        }).apply()
    }
    private fun readTimer(): FocusTimerState? = try {
        sp.getString("timer", null)?.let { val j = JSONObject(it)
            FocusTimerState(j.getLong("s"), j.getLong("e"), j.getInt("m"), j.optString("sub"), j.optString("ch"), j.optString("st", "Concept Study"), j.optBoolean("b"), j.optBoolean("bl", true)) }
    } catch (_: Exception) { null }

    // ───── Blocker ─────
    var blockerOn by mutableStateOf(sp.getBoolean("blockerOn", true)); private set
    fun updateBlockerOn(v: Boolean) { blockerOn = v; sp.edit().putBoolean("blockerOn", v).apply() }

    var platforms by mutableStateOf(sp.getStringSet("platforms", ALL_PLATFORMS.toSet())!!.toSet()); private set
    fun togglePlatform(p: String, on: Boolean) {
        platforms = if (on) platforms + p else platforms - p
        sp.edit().putStringSet("platforms", platforms).apply()
    }

    var blockDuringTimer by mutableStateOf(sp.getBoolean("blockDuringTimer", true)); private set
    fun updateBlockDuringTimer(v: Boolean) { blockDuringTimer = v; sp.edit().putBoolean("blockDuringTimer", v).apply() }

    var windows by mutableStateOf(readWins("windows", listOf(Win(16 * 60, 22 * 60)))); private set
    fun updateWindows(w: List<Win>) { windows = w; writeWins("windows", w) }

    var debugLog by mutableStateOf(sp.getBoolean("debugLog", false)); private set
    fun updateDebugLog(v: Boolean) { debugLog = v; sp.edit().putBoolean("debugLog", v).apply() }

    var blocksToday by mutableIntStateOf(readBlocks()); private set
    private fun readBlocks() = if (sp.getString("blockDay", "") == LocalDate.now().toString()) sp.getInt("blocks", 0) else 0
    fun countBlock() {
        val today = LocalDate.now().toString()
        val n = (if (sp.getString("blockDay", "") == today) sp.getInt("blocks", 0) else 0) + 1
        sp.edit().putString("blockDay", today).putInt("blocks", n).apply()
        blocksToday = n
    }

    fun timerRunning(): Boolean { val t = timer; return t != null && t.endMs > System.currentTimeMillis() }

    fun inWindow(nowMin: Int): Boolean = windows.any {
        it.on && (if (it.start <= it.end) nowMin in it.start until it.end else nowMin >= it.start || nowMin < it.end)
    }

    /** True while distractions should be blocked right now. */
    fun blockingActive(): Boolean {
        val t = timer
        if (t != null && !t.isBreak && t.block && blockDuringTimer && t.endMs > System.currentTimeMillis()) return true
        val now = LocalTime.now()
        return blockerOn && inWindow(now.hour * 60 + now.minute)
    }

    // ───── Reminders ─────
    var remindersOn by mutableStateOf(sp.getBoolean("remindersOn", true)); private set
    fun updateRemindersOn(v: Boolean) { remindersOn = v; sp.edit().putBoolean("remindersOn", v).apply() }

    var reminders by mutableStateOf(readRems()); private set
    fun updateReminders(r: List<Rem>) {
        reminders = r
        sp.edit().putString("reminders", JSONArray(r.map { JSONObject().put("m", it.min).put("o", it.on) }).toString()).apply()
    }
    private fun readRems(): List<Rem> = try {
        JSONArray(sp.getString("reminders", null) ?: throw Exception()).objs().map { Rem(it.optInt("m"), it.optBoolean("o", true)) }
    } catch (_: Exception) { listOf(Rem(7 * 60 + 30), Rem(17 * 60 + 30), Rem(21 * 60)) }

    var dailyGoal by mutableIntStateOf(sp.getInt("dailyGoal", 120)); private set
    fun updateDailyGoal(v: Int) { dailyGoal = v.coerceIn(15, 720); sp.edit().putInt("dailyGoal", dailyGoal).apply() }

    var skipIfDone by mutableStateOf(sp.getBoolean("skipIfDone", true)); private set
    fun updateSkipIfDone(v: Boolean) { skipIfDone = v; sp.edit().putBoolean("skipIfDone", v).apply() }

    // ───── Project M ─────
    var signals by mutableStateOf(sp.getBoolean("signals", true)); private set
    fun updateSignals(v: Boolean) { signals = v; sp.edit().putBoolean("signals", v).apply() }

    var seenTeasers by mutableStateOf(sp.getStringSet("seen", emptySet())!!.toSet()); private set
    fun markSeen(code: String) { seenTeasers = seenTeasers + code; sp.edit().putStringSet("seen", seenTeasers).apply() }

    private fun readWins(key: String, def: List<Win>): List<Win> = try {
        JSONArray(sp.getString(key, null) ?: throw Exception()).objs().map { Win(it.optInt("s"), it.optInt("e"), it.optBoolean("o", true)) }
    } catch (_: Exception) { def }
    private fun writeWins(key: String, w: List<Win>) {
        sp.edit().putString(key, JSONArray(w.map { JSONObject().put("s", it.start).put("e", it.end).put("o", it.on) }).toString()).apply()
    }

    companion object {
        @Volatile private var inst: Cfg? = null
        fun get(ctx: Context): Cfg = inst ?: synchronized(this) { inst ?: Cfg(ctx.applicationContext).also { inst = it } }
    }
}
