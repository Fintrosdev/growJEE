package com.growjee.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notif.ensureChannels(this)
        Focus.settleIfExpired(this)
        Alarms.rescheduleAll(this)
        setContent { GrowTheme { AppRoot(vm) } }
    }

    override fun onResume() {
        super.onResume()
        Focus.settleIfExpired(this)
        Bus.tick++
    }
}

private val EDITABLE = setOf("Daily Study Log", "Question Bank Log", "Mock Test Analytics", "Mistake Register", "Revision Scheduler", "Target Planner")

@Composable
fun AppRoot(vm: AppVm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)

    var page by rememberSaveable { mutableStateOf("Dashboard") }
    var editing by remember { mutableStateOf<Pair<String, JSONObject?>?>(null) }
    var mock by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var account by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Alarms.rescheduleAll(ctx) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> if (u != null) vm.exportTo(u) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.importFrom(u) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notif.canPost(ctx)) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { m -> toast = m; kotlinx.coroutines.delay(2400); if (toast == m) toast = null }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && page != "Dashboard") { page = "Dashboard" }

    fun go(p: String) { page = p; scope.launch { drawer.close() } }

    CompositionLocalProvider(LocalContentColor provides C.Main) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = C.Sheet, drawerContentColor = C.Main) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        GlitchText("GrowJEE", 26f, C.Main, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                        GText("PROJECT M", 11f, C.Cyan, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
                        PAGES.forEach { p ->
                            val on = p == page
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (on) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                                    .clickable { go(p) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GText(ICONS[p] ?: "•", 16f, if (on) C.Cyan else C.Dim, modifier = Modifier.size(28.dp))
                                GText(p, 15f, if (on) C.Main else C.Muted, if (on) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                        GhostButton("Account & sync", { scope.launch { drawer.close() }; account = true }, Modifier.fillMaxWidth())
                        GhostButton("Export backup", { scope.launch { drawer.close() }; exporter.launch("growjee-backup.json") }, Modifier.fillMaxWidth().padding(top = 8.dp), C.Blue)
                        GhostButton("Import backup", { scope.launch { drawer.close() }; importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.fillMaxWidth().padding(top = 8.dp), C.Blue)
                    }
                }
            }
        ) {
            GlassBackground {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { scope.launch { drawer.open() } },
                                contentAlignment = Alignment.Center
                            ) { GText("☰", 18f, C.Main) }
                            Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                GlitchText(page, 22f, C.Main, key = page)
                            }
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { account = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(
                                    when { vm.syncing -> C.Amber; vm.session != null -> C.Green; else -> C.Dim }
                                ))
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = page,
                                transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 }) togetherWith (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 48 }) },
                                label = "page"
                            ) { p ->
                                when (p) {
                                    "Dashboard" -> DashboardScreen(vm, onNav = { go(it) }, onAdd = { editing = it to null })
                                    "Chapter Master" -> ChapterScreen(vm, onEdit = { editing = "Chapter Master" to it }, onDelete = { deleting = "master" to it })
                                    "Focus Timer" -> FocusScreen(vm)
                                    "Distraction Blocker" -> BlockerScreen()
                                    "Reminders" -> RemindersScreen()
                                    else -> RecordsScreen(vm, p, onEdit = { editing = p to it }, onMock = { mock = it }, onDelete = { k, o -> deleting = k to o })
                                }
                            }
                        }
                    }

                    // FAB (only where something can be added)
                    if (page == "Chapter Master" || page in EDITABLE) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).size(58.dp).clip(CircleShape)
                                .background(C.Cyan).clickable { editing = page to null },
                            contentAlignment = Alignment.Center
                        ) { GText("+", 28f, C.Ink, FontWeight.Bold) }
                    }

                    // Glass snackbar
                    androidx.compose.animation.AnimatedVisibility(
                        visible = toast != null,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp, start = 24.dp, end = 96.dp),
                        enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(C.Sheet).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            GText(toast.orEmpty(), 13f, C.Main)
                        }
                    }
                }
            }
        }

        editing?.let { (p, cur) -> EditSheet(vm, p, cur) { editing = null } }
        mock?.let { m ->
            MockSheet(vm, m, onEdit = { mock = null; editing = "Mock Test Analytics" to m }, onClose = { mock = null })
        }
        if (account) AccountSheet(vm) { account = false }
        deleting?.let { (k, o) ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                containerColor = C.Sheet,
                title = { Text("Delete this entry?", color = C.Main) },
                text = { Text("This can't be undone.", color = C.Muted) },
                confirmButton = { TextButton({ vm.delete(k, o); deleting = null }) { Text("Delete", color = C.Rose) } },
                dismissButton = { TextButton({ deleting = null }) { Text("Cancel", color = C.Muted) } }
            )
        }
    }
}
