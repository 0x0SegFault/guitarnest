package app.guitarnest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitarnest.R
import app.guitarnest.practice.MAX_BPM
import app.guitarnest.practice.MIN_BPM

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeScreen(settings: MetronomeSettings, playing: Playing?, vm: SongsViewModel, modifier: Modifier = Modifier) {
    val mine = playing?.takeIf { it.songId == null }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tempo
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Step("−5") { vm.updateMetronome { it.copy(bpm = it.bpm - 5) } }
            Step("−1") { vm.updateMetronome { it.copy(bpm = it.bpm - 1) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("${mine?.bpm?.takeIf { settings.trainer } ?: settings.bpm}", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 56.sp)
                Text("BPM", style = MaterialTheme.typography.labelLarge)
            }
            Step("+1") { vm.updateMetronome { it.copy(bpm = it.bpm + 1) } }
            Step("+5") { vm.updateMetronome { it.copy(bpm = it.bpm + 5) } }
        }
        Slider(
            value = settings.bpm.toFloat(),
            onValueChange = { v -> vm.updateMetronome { it.copy(bpm = v.toInt()) } },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mine == null) {
                Button(onClick = vm::startMetronome) { Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.start)) }
            } else {
                Button(onClick = vm::stop) { Icon(Icons.Filled.Stop, null); Text(stringResource(R.string.stop)) }
            }
            FilledTonalButton(onClick = vm::tapTempo) { Text(stringResource(R.string.tap_tempo)) }
        }

        // Beats
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(settings.beatsPerBar) { b ->
                val on = mine != null && mine.beat == b
                Box(
                    Modifier.size(if (b == 0) 28.dp else 22.dp).background(
                        when {
                            on && b == 0 -> MaterialTheme.colorScheme.primary
                            on -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        CircleShape,
                    )
                )
            }
        }
        settings.pattern?.let { p -> StrumRow(p, active = mine?.let { patternStep(p, it.step) }, big = true) }
        Label(R.string.beats_per_bar)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2, 3, 4, 5, 6, 7).forEach { n ->
                FilterChip(selected = settings.beatsPerBar == n, onClick = { vm.updateMetronome { it.copy(beatsPerBar = n) } }, label = { Text("$n") })
            }
        }
        Label(R.string.clicks)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "♩", 2 to "♪♪", 3 to "3", 4 to "♬♬").forEach { (n, label) ->
                FilterChip(selected = settings.subdivisions == n, onClick = { vm.updateMetronome { it.copy(subdivisions = n) } }, label = { Text(label) })
            }
        }

        // Strumming
        Label(R.string.strumming_pattern)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = settings.pattern == null, onClick = { vm.updateMetronome { it.copy(pattern = null) } }, label = { Text(stringResource(R.string.none)) })
            PRESET_PATTERNS.forEach { p ->
                FilterChip(selected = settings.pattern?.id == p.id, onClick = { vm.updateMetronome { it.copy(pattern = p) } }, label = { Text(p.name) })
            }
        }

        // Speed trainer
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.speed_trainer), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.speed_trainer_detail, settings.bpm, settings.trainerTarget, settings.trainerStep, settings.trainerBars),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.trainer, onCheckedChange = { on -> vm.updateMetronome { it.copy(trainer = on) } })
        }
        if (settings.trainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.target_tempo))
                OutlinedButton(onClick = { vm.updateMetronome { it.copy(trainerTarget = (it.trainerTarget - 5).coerceAtLeast(MIN_BPM)) } }) { Text("−5") }
                Text("${settings.trainerTarget}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.updateMetronome { it.copy(trainerTarget = (it.trainerTarget + 5).coerceAtMost(MAX_BPM)) } }) { Text("+5") }
            }
        }

    }
}

@Composable
private fun Step(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.width(52.dp)) { Text(text, maxLines = 1) }
}

@Composable
private fun Label(id: Int) {
    Text(stringResource(id), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}
