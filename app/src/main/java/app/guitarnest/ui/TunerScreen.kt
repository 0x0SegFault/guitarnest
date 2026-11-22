package app.guitarnest.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.guitarnest.R
import app.guitarnest.tuner.Tuning
import app.guitarnest.tuner.readingOf
import app.guitarnest.ui.theme.TunerColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Within this many cents the string counts as in tune (a common tuner tolerance). */
private const val IN_TUNE_CENTS = 5.0
private const val CLOSE_CENTS = 15.0

/** What the tuner shows for the current sound. */
private data class Reading(val note: String, val cents: Double?, val string: Int?)

private fun currentReading(state: TunerState): Reading {
    val f = state.frequency ?: return Reading("–", null, state.lockedString)
    val a4 = state.a4.toDouble()
    val target = state.activeString
    // Against the selected (or nearest) string when it is within a semitone; else against the nearest note.
    if (target != null) {
        val cents = state.tuning.centsOff(target, f, a4)
        if (abs(cents) <= 100) return Reading(state.tuning.names[target].dropLast(1), cents, target)
    }
    val nearest = readingOf(f, a4)
    return Reading(nearest.name.dropLast(1), nearest.cents, target)
}

@Composable
private fun toneOf(cents: Double?): Color {
    val colors = TunerColors.current
    val tone = when {
        cents == null -> MaterialTheme.colorScheme.outline
        abs(cents) <= IN_TUNE_CENTS -> colors.inTune
        abs(cents) <= CLOSE_CENTS -> colors.close
        else -> colors.off
    }
    return animateColorAsState(tone, label = "tone").value
}

@Composable
fun TunerScreen(modifier: Modifier = Modifier, vm: TunerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }

    // Listen only while the screen is visible.
    LifecycleResumeEffect(granted) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.start()
        onPauseOrDispose { vm.stop() }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val reading = currentReading(state)
    // An older reading stays on screen, dimmed, so the player can read it while the string rings out.
    val color = toneOf(reading.cents).copy(alpha = if (state.fresh) 1f else 0.55f)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TuningPicker(state.tuning, vm::selectTuning)
        if (!granted) {
            MicPermissionCard(
                permanentlyDenied = asked,
                onAllow = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
        } else if (state.micError) {
            Text(stringResource(R.string.mic_error), color = MaterialTheme.colorScheme.error)
        }
        Gauge(reading, color, state.listening, state.frequency)
        GuitarStrings(state, reading, color, vm::toggleString)
        ReferencePitch(state.a4, vm::setA4)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningPicker(selected: Tuning, onSelect: (Tuning) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = tuningLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.tuning)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Tuning.PRESETS.forEach { t ->
                DropdownMenuItem(
                    text = { Text(tuningLabel(t)) },
                    onClick = { onSelect(t); open = false },
                )
            }
        }
    }
}

@Composable
fun tuningLabel(t: Tuning): String {
    val name = when (t.id) {
        "standard" -> stringResource(R.string.tuning_standard)
        "half_step_down" -> stringResource(R.string.tuning_half_step_down)
        "whole_step_down" -> stringResource(R.string.tuning_whole_step_down)
        "drop_d" -> stringResource(R.string.tuning_drop_d)
        "drop_c" -> stringResource(R.string.tuning_drop_c)
        "dadgad" -> "DADGAD"
        "open_d" -> stringResource(R.string.tuning_open_d)
        "open_g" -> stringResource(R.string.tuning_open_g)
        "open_e" -> stringResource(R.string.tuning_open_e)
        else -> t.id
    }
    return "$name · " + t.names.joinToString(" ") { it.dropLast(1) }
}

@Composable
private fun MicPermissionCard(permanentlyDenied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Text(stringResource(R.string.mic_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.mic_why), style = MaterialTheme.typography.bodyMedium)
            if (permanentlyDenied) {
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            } else {
                Button(onClick = onAllow) { Text(stringResource(R.string.mic_allow)) }
            }
        }
    }
}

@Composable
private fun Gauge(reading: Reading, color: Color, listening: Boolean, frequency: Double?) {
    val cents = reading.cents
    val needle by animateFloatAsState(
        (cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "needle",
    )
    val status = when {
        cents == null -> stringResource(if (listening) R.string.play_a_string else R.string.not_listening)
        abs(cents) <= IN_TUNE_CENTS -> stringResource(R.string.in_tune)
        cents < 0 -> stringResource(R.string.too_low)
        else -> stringResource(R.string.too_high)
    }
    val description = if (cents == null) status
    else pluralStringResource(R.plurals.reading_description, abs(cents.roundToInt()), reading.note, cents.roundToInt(), status)

    Box(
        Modifier.fillMaxWidth().aspectRatio(1.8f).semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val track = MaterialTheme.colorScheme.outlineVariant
        val tick = MaterialTheme.colorScheme.onSurfaceVariant
        val good = TunerColors.current.inTune
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.8f)) {
            val radius = size.width * 0.42f
            val centre = Offset(size.width / 2, size.height * 0.98f)
            val sweep = 140f
            val start = 270f - sweep / 2
            val stroke = size.width * 0.03f
            val arcTopLeft = Offset(centre.x - radius, centre.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            fun toAngle(c: Double) = Math.toRadians(270.0 + c / 50.0 * sweep / 2)
            drawArc(track, start, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val goodSweep = (IN_TUNE_CENTS / 50.0 * sweep / 2).toFloat()
            drawArc(good, 270f - goodSweep, goodSweep * 2, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            for (c in -50..50 step 10) {
                val a = toAngle(c.toDouble())
                val inner = radius - stroke * (if (c == 0) 2.6f else 1.6f)
                val outer = radius - stroke * 0.9f
                drawLine(tick, Offset(centre.x + inner * cos(a).toFloat(), centre.y + inner * sin(a).toFloat()),
                    Offset(centre.x + outer * cos(a).toFloat(), centre.y + outer * sin(a).toFloat()), strokeWidth = 2.dp.toPx())
            }
            // Short pointer near the scale, so it never covers the note name.
            if (cents != null) {
                val a = toAngle(needle.toDouble())
                val inner = radius * 0.72f
                val tip = Offset(centre.x + radius * cos(a).toFloat(), centre.y + radius * sin(a).toFloat())
                drawLine(color, Offset(centre.x + inner * cos(a).toFloat(), centre.y + inner * sin(a).toFloat()), tip,
                    strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color, radius = stroke * 0.9f, center = tip)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(reading.note, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, color = color, lineHeight = 72.sp)
            Text(
                if (cents == null) " " else "%+d ¢".format(cents.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(status, style = MaterialTheme.typography.titleLarge, color = color)
        if (frequency != null) {
            Text(
                "  ·  %.1f Hz".format(frequency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The six strings, low E on the left as seen from the player, with their gauge.
 * The string being tuned lights up in the tuner colour. Tap a string to lock it; tap again to unlock.
 */
@Composable
private fun GuitarStrings(state: TunerState, reading: Reading, color: Color, onToggle: (Int) -> Unit) {
    val names = state.tuning.names
    val idle = MaterialTheme.colorScheme.outline
    val board = MaterialTheme.colorScheme.surfaceContainerHigh
    val nut = MaterialTheme.colorScheme.outlineVariant
    val active = reading.string
    val locked = state.lockedString
    Row(
        Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium).background(board),
    ) {
        names.forEachIndexed { i, name ->
            val on = i == active && (reading.cents != null || i == locked)
            val label = stringResource(R.string.string_description, 6 - i, name)
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .toggleable(value = i == locked, onValueChange = { onToggle(i) }, role = Role.Checkbox)
                    .semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.padding(top = 10.dp).size(40.dp).clip(CircleShape)
                        .background(if (on) color else Color.Transparent)
                        .border(2.dp, if (i == locked) color else nut, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.dropLast(1),
                        fontWeight = FontWeight.Bold,
                        color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    // Real string gauges run from about 0.25 mm (high E) to 1.15 mm (low E).
                    val width = (4.5f - i * 0.6f).dp.toPx()
                    drawLine(nut, Offset(0f, 10.dp.toPx()), Offset(size.width, 10.dp.toPx()), strokeWidth = 4.dp.toPx())
                    drawLine(
                        if (on) color else idle,
                        Offset(size.width / 2, 10.dp.toPx()),
                        Offset(size.width / 2, size.height),
                        strokeWidth = if (on) width * 1.6f else width,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferencePitch(a4: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reference_pitch), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange(a4 - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("A4 = $a4 Hz", style = MaterialTheme.typography.titleSmall, modifier = Modifier.size(width = 96.dp, height = 24.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange(a4 + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        if (a4 != 440) TextButton(onClick = { onChange(440) }) { Text(stringResource(R.string.reset)) }
    }
}
