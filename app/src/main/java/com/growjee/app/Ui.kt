package com.growjee.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

object C {
    val Ink = Color(0xFF080A10)
    val Cyan = Color(0xFF75F4E8)
    val Blue = Color(0xFFA7AFFF)
    val Main = Color(0xFFF4F6FF)
    val Muted = Color(0xFFA8B1C7)
    val Dim = Color(0xFF7C86A2)
    val Gold = Color(0xFFFFD27D)
    val Green = Color(0xFF5BE3A0)
    val Amber = Color(0xFFFFC470)
    val Rose = Color(0xFFFF8FA3)
    val Phys = Color(0xFF6CC8FF)
    val Chem = Color(0xFFFF9EC4)
    val MathC = Color(0xFFB8A4FF)
    val Sheet = Color(0xF20C0F18)

    fun subject(s: String) = when (s) { "Physics" -> Phys; "Chemistry" -> Chem; "Mathematics" -> MathC; else -> Blue }
    fun pct(p: Double) = if (p >= 75) Green else if (p >= 45) Amber else Rose
}

@Composable
fun GrowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = C.Cyan, onPrimary = C.Ink, background = C.Ink, surface = C.Ink, onSurface = C.Main, onBackground = C.Main),
        content = content
    )
}

@Composable
fun GText(
    text: String, size: Float = 14f, color: Color = C.Main, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null
) = Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)

// ───────────────────────── Background & surfaces ─────────────────────────
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "orbs")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF0C0F18), Color(0xFF06070B), Color(0xFF0A0B14))))
            .drawBehind {
                orb(Offset(size.width * (0.15f + 0.6f * a), size.height * (0.04f + 0.10f * b)), size.width * 0.95f, Color(0xFF6CE7E2).copy(alpha = 0.20f))
                orb(Offset(size.width * (0.95f - 0.5f * b), size.height * (0.92f - 0.12f * a)), size.width * 1.05f, Color(0xFF9A8CFF).copy(alpha = 0.18f))
                orb(Offset(size.width * (0.05f + 0.3f * a), size.height * (0.62f + 0.10f * b)), size.width * 0.7f, Color(0xFFFF7EC8).copy(alpha = 0.09f))
            },
        content = content
    )
}

private fun DrawScope.orb(c: Offset, r: Float, col: Color) {
    drawCircle(Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    var m = modifier
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.12f))), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/** Fade + rise into place once, staggered by index. */
fun Modifier.reveal(index: Int = 0): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!shown) { delay(minOf(index, 10) * 55L); p.animateTo(1f, tween(520, easing = FastOutSlowInEasing)); shown = true }
    }
    graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 48f }
}

// ───────────────────────── Buttons & controls ─────────────────────────
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(C.Cyan, C.Blue)))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { GText(text, 14f, C.Ink, FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Main) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { GText(text, 13.5f, color, FontWeight.SemiBold) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Brush.linearGradient(listOf(C.Cyan, C.Blue)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    ) { GText(text, 12.5f, if (selected) C.Ink else C.Muted, FontWeight.SemiBold) }
}

@Composable
fun Badge(text: String, color: Color) {
    if (text.isBlank()) return
    Box(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        GText(text, 11f, color, FontWeight.SemiBold)
    }
}

@Composable
fun SwitchRow(title: String, detail: String = "", checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            GText(title, 14.5f, C.Main, FontWeight.SemiBold)
            if (detail.isNotBlank()) GText(detail, 12f, C.Muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Ink, checkedTrackColor = C.Cyan, uncheckedThumbColor = C.Muted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    focusedTextColor = C.Main, unfocusedTextColor = C.Main, cursorColor = C.Cyan,
    focusedLabelColor = C.Cyan, unfocusedLabelColor = C.Muted,
    focusedContainerColor = Color.Black.copy(alpha = 0.25f), unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
)

@Composable
fun ChoiceField(label: String, value: String, options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Text("▾", color = C.Muted) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp)
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color(0xF2161C2B)) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o, color = C.Main) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Sheet, contentColor = C.Main, scrimColor = Color.Black.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), content = content)
    }
}

// ───────────────────────── Progress ─────────────────────────
@Composable
fun Bar(ratio: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, delayMs: Int = 0) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) ratio.coerceIn(0.0, 1.0).toFloat() else 0f, tween(950, delayMs + 250, FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f)))))
    }
}

@Composable
fun ProgressRow(label: String, right: String, ratio: Double, color: Color, delay: Int = 0) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GText(label, 13f, C.Main, FontWeight.SemiBold)
            GText(right, 12f, C.Muted)
        }
        Bar(ratio, color, Modifier.padding(top = 6.dp), delayMs = delay)
    }
}

@Composable
fun ListRow(title: String, detail: String, right: String = "", tint: Color = C.Blue, rightColor: Color = C.Cyan) {
    Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp, end = 11.dp).size(8.dp).clip(CircleShape).background(tint))
        Column(Modifier.weight(1f)) {
            GText(title, 13.5f, C.Main, FontWeight.SemiBold, maxLines = 1)
            GText(detail, 11.5f, C.Muted)
        }
        if (right.isNotBlank()) GText(right, 12.5f, rightColor, FontWeight.SemiBold, Modifier.padding(start = 10.dp))
    }
}

// ───────────────────────── Glitch effects ─────────────────────────
/** Text that occasionally RGB-splits and jitters. Also glitches once when it first appears or when [key] changes. */
@Composable
fun GlitchText(
    text: String, size: Float, color: Color = C.Main, weight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier, intensity: Float = 1f, key: Any = text, calm: Boolean = false
) {
    var burst by remember { mutableStateOf(0f) }
    LaunchedEffect(key) {
        repeat(5) { burst = Random.nextFloat(); delay(45) }
        burst = 0f
        while (!calm) {
            delay(Random.nextLong(3500, 8000))
            repeat(Random.nextInt(3, 7)) { burst = Random.nextFloat(); delay(40) }
            burst = 0f
        }
    }
    val off = burst * 9f * intensity
    val dy = (Random.nextFloat() - 0.5f) * 5f * burst * intensity
    Box(modifier) {
        if (burst > 0f) {
            GText(text, size, C.Rose.copy(alpha = 0.85f), weight, Modifier.offset { IntOffset((-off).roundToInt(), dy.roundToInt()) })
            GText(text, size, C.Cyan.copy(alpha = 0.85f), weight, Modifier.offset { IntOffset(off.roundToInt(), (-dy).roundToInt()) })
        }
        GText(text, size, color, weight, Modifier.offset { IntOffset((off * 0.3f).roundToInt(), 0) })
    }
}

private const val NOISE = "▓▒░█#@%&§¤<>/\\|"

/** Locked: endlessly scrambling. Unlocked: decodes from noise to the real text once. */
@Composable
fun DecodeText(target: String, locked: Boolean, size: Float, color: Color, modifier: Modifier = Modifier) {
    var shown by remember(target, locked) { mutableStateOf(scramble(target, 0f)) }
    LaunchedEffect(target, locked) {
        if (locked) {
            while (true) { shown = scramble(target, 0f); delay(90) }
        } else {
            val steps = 22
            for (i in 0..steps) { shown = scramble(target, i / steps.toFloat()); delay(40) }
            shown = target
        }
    }
    GText(shown, size, color, FontWeight.SemiBold, modifier)
}

private fun scramble(t: String, reveal: Float): String {
    val n = (t.length * reveal).toInt()
    return buildString { t.forEachIndexed { i, ch -> append(if (ch == ' ' || i < n) ch else NOISE[Random.nextInt(NOISE.length)]) } }
}

/** CRT scanlines plus a slow bright band sweeping down. */
fun Modifier.scanlines(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "y")
    drawWithContent {
        drawContent()
        var yy = 0f
        while (yy < size.height) { drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, yy), Offset(size.width, yy), 1f); yy += 4f }
        val by = size.height * y
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, C.Cyan.copy(alpha = 0.10f), Color.Transparent), startY = by - 40f, endY = by + 40f))
    }
}
