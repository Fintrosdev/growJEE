package com.growjee.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Detects the Reels / Shorts screens of a few apps and sends you back.
 *
 * It only runs inside the listed packages (see res/xml/blocker_config.xml), only looks at view ids and
 * tab labels, and never stores or sends anything. Detection relies on those apps' internal layouts, so an
 * app update can break it; turn on "Log screen ids" in the Blocker page to see what the app exposes.
 */
class ReelsBlockerService : AccessibilityService() {
    private var lastScan = 0L
    private var lastBlock = 0L
    private var lastToast = 0L
    private var strikes = 0
    private val main = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val platform = platformOf(pkg) ?: return
        val cfg = Cfg.get(this)
        if (platform !in cfg.platforms || !cfg.blockingActive()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastScan < 250) return          // content-changed events are very chatty
        lastScan = now

        if (platform == "tiktok") { block(whole = true); return }

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val budget = intArrayOf(600)
        val hit = scan(root, platform, budget, cfg.debugLog)
        if (hit != null) {
            if (cfg.debugLog) Log.d(TAG, "BLOCK $platform via $hit")
            block(whole = false)
        }
    }

    /** Returns what matched (for logging), or null. */
    private fun scan(node: AccessibilityNodeInfo?, platform: String, budget: IntArray, debug: Boolean): String? {
        if (node == null || budget[0]-- <= 0) return null
        val id = node.viewIdResourceName.orEmpty()
        if (debug && id.isNotEmpty()) Log.d(TAG, "$platform id=$id sel=${node.isSelected} desc=${node.contentDescription}")

        val idMatch = when (platform) {
            "instagram" -> ID_INSTAGRAM.any { id.contains(it) }
            "youtube" -> ID_YOUTUBE.any { id.contains(it) }
            else -> false
        }
        if (idMatch) return "id:$id"

        // The selected bottom tab labelled "Reels" / "Shorts".
        if (node.isSelected) {
            val label = (node.contentDescription ?: node.text)?.toString()?.trim()?.lowercase().orEmpty()
            val tab = when (platform) { "youtube" -> "shorts"; else -> "reels" }
            if (label == tab || label.startsWith("$tab,") || label.startsWith("$tab tab")) return "tab:$label"
        }

        for (i in 0 until node.childCount) scan(node.getChild(i), platform, budget, debug)?.let { return it }
        return null
    }

    private fun block(whole: Boolean) {
        val now = SystemClock.elapsedRealtime()
        strikes = if (now - lastBlock < 1500) strikes + 1 else 0
        lastBlock = now
        // First try Back. If the app keeps landing on the same screen, go Home.
        performGlobalAction(if (whole || strikes >= 2) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        Cfg.get(this).countBlock()
        if (now - lastToast > 4000) {
            lastToast = now
            main.post { Toast.makeText(applicationContext, "Blocked. Back to study.", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "GrowJEE-Blocker"
        private val ID_INSTAGRAM = listOf("clips_viewer", "clips_video_container", "reel_viewer", "clips_swipe")
        private val ID_YOUTUBE = listOf("reel_player", "shorts_player", "reel_recycler", "reel_watch")

        fun platformOf(pkg: String): String? = when (pkg) {
            "com.instagram.android" -> "instagram"
            "com.google.android.youtube" -> "youtube"
            "com.facebook.katana" -> "facebook"
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "tiktok"
            else -> null
        }

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, ReelsBlockerService::class.java).flattenToString()
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
