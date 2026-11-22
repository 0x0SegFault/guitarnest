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
import androidx.compose.material3.FilterChip
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
import app.guitarnest.tuner.Notes
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
        Readout(state)
        StringRow(state, vm::toggleString)
        ReferencePitch(state.a4, vm::setA4)
        Text(
            stringResource(R.string.capo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun Readout(state: TunerState) {
    val a4 = state.a4.toDouble()
    val f = state.frequency
    val target = state.activeString
    // Cents against the selected string when one is locked or found; else against the nearest note.
    val cents = when {
        f == null -> null
        target != null -> state.tuning.centsOff(target, f, a4)
        else -> readingOf(f, a4).cents
    }
    val noteName = when {
        f == null -> "–"
        target != null && abs(cents ?: 0.0) <= 100 -> Notes.name(state.tuning.strings[target])
        else -> readingOf(f, a4).name
    }
    val colors = TunerColors.current
    val tone = when {
        cents == null -> MaterialTheme.colorScheme.outline
        abs(cents) <= IN_TUNE_CENTS -> colors.inTune
        abs(cents) <= CLOSE_CENTS -> colors.close
        else -> colors.off
    }
    val color by animateColorAsState(tone, label = "tone")
    val needle by animateFloatAsState(
        (cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "needle",
    )
    val advice = when {
        cents == null -> stringResource(if (state.listening) R.string.play_a_string else R.string.not_listening)
        abs(cents) <= IN_TUNE_CENTS -> stringResource(R.string.in_tune)
        cents < 0 -> stringResource(R.string.tune_up)
        else -> stringResource(R.string.tune_down)
    }
    val description = if (cents == null) advice else pluralStringResource(R.plurals.reading_description, abs(cents.roundToInt()), noteName, cents.roundToInt(), advice)

    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val track = MaterialTheme.colorScheme.outlineVariant
        val tick = MaterialTheme.colorScheme.onSurfaceVariant
        val good = colors.inTune
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val radius = size.width * 0.44f
            val centre = Offset(size.width / 2, size.height * 0.92f)
            val sweep = 140f
            val start = 270f - sweep / 2
            val stroke = size.width * 0.03f
            fun toAngle(c: Double) = Math.toRadians(270.0 + c / 50.0 * sweep / 2)
            drawArc(track, start, sweep, false, Offset(centre.x - radius, centre.y - radius),
                androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            val goodSweep = (IN_TUNE_CENTS / 50.0 * sweep / 2).toFloat()
            drawArc(good, 270f - goodSweep, goodSweep * 2, false, Offset(centre.x - radius, centre.y - radius),
                androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            for (c in -50..50 step 10) {
                val a = toAngle(c.toDouble())
                val inner = radius - stroke * (if (c == 0) 2.6f else 1.6f)
                val outer = radius - stroke * 0.9f
                drawLine(tick, Offset(centre.x + inner * cos(a).toFloat(), centre.y + inner * sin(a).toFloat()),
                    Offset(centre.x + outer * cos(a).toFloat(), centre.y + outer * sin(a).toFloat()), strokeWidth = 2.dp.toPx())
            }
            if (cents != null) {
                val a = toAngle(needle.toDouble())
                val len = radius * 0.9f
                drawLine(color, centre, Offset(centre.x + len * cos(a).toFloat(), centre.y + len * sin(a).toFloat()),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(color, radius = 8.dp.toPx(), center = centre)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 36.dp)) {
            Text(noteName, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(
                if (cents == null) " " else "%+d ¢".format(cents.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        advice,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        color = color,
    )
    if (f != null) {
        Text(
            "%.1f Hz".format(f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StringRow(state: TunerState, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (state.lockedString == null) R.string.strings_auto else R.string.strings_locked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.tuning.strings.forEachIndexed { i, midi ->
                val name = Notes.name(midi)
                val label = stringResource(R.string.string_description, 6 - i, name)
                FilterChip(
                    selected = state.activeString == i,
                    onClick = { onToggle(i) },
                    label = { Text(name.dropLast(1), fontWeight = FontWeight.Bold) },
                    modifier = Modifier.semantics {
                        contentDescription = label
                    },
                )
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
