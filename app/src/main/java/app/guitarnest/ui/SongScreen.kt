package app.guitarnest.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.guitarnest.R
import app.guitarnest.data.PracticeEntry
import app.guitarnest.tab.Song
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongScreen(song: Song, playing: Playing?, log: List<PracticeEntry>, vm: SongsViewModel, modifier: Modifier = Modifier) {
    var tempoPercent by rememberSaveable { mutableIntStateOf(100) }
    var loop by rememberSaveable { mutableStateOf<Int?>(null) }
    var trainer by rememberSaveable { mutableStateOf(false) }
    val options = PlayOptions(tempoPercent, loop, trainer)
    val mine = playing?.takeIf { it.songId == song.id }
    val order = vm.bars(song, options)
    val current = mine?.bar?.takeIf { it >= 0 && order.isNotEmpty() }?.let { order[it % order.size] }

    val scroll = rememberScrollState()
    val barTops = remember(song) { mutableMapOf<Pair<Int, Int>, Int>() }
    LaunchedEffect(current?.section, current?.bar) {
        val top = current?.let { barTops[it.section to it.bar] } ?: return@LaunchedEffect
        scroll.animateScrollTo((top - 200).coerceAtLeast(0))
    }
    Column(modifier) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header(song, log)
            song.sections.forEachIndexed { s, section ->
                val pattern = song.pattern(section.strum)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.name, style = MaterialTheme.typography.titleMedium)
                    if (section.repeat > 1) Text("×${section.repeat}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    pattern?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    section.bars.forEachIndexed { b, bar ->
                        val active = current != null && current.section == s && current.bar == b
                        val barPattern = song.pattern(bar.strum) ?: pattern
                        Box(Modifier.onGloballyPositioned { barTops[s to b] = (it.positionInParent().y + (it.parentLayoutCoordinates?.positionInParent()?.y ?: 0f)).toInt() }) {
                            BarCard(bar, song.beatsPerBar, barPattern, active, barPattern?.let { p -> mine?.let { patternStep(p, it.step) } }, b + 1)
                        }
                    }
                }
            }
            if (song.notes.isNotBlank()) Text(song.notes, style = MaterialTheme.typography.bodyMedium)
            FindOnline(song)
        }
        Controls(song, mine, options, onTempo = { tempoPercent = it }, onLoop = { loop = it }, onTrainer = { trainer = it },
            onPlay = { vm.startSong(song, options) }, onStop = vm::stop)
    }
}

@Composable
private fun Header(song: Song, log: List<PracticeEntry>) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (song.artist.isNotBlank()) Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val tuning = song.tuningNotes()
        val facts = listOfNotNull(
            tuning?.let { t -> tuningLabel(t) },
            if (song.capo > 0) stringResource(R.string.capo_n, song.capo) else stringResource(R.string.no_capo),
            "${song.tempo} BPM · ${song.beatsPerBar}/4",
            levelLabel(song.level),
        )
        Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
        if (song.source.isNotBlank()) Text(stringResource(R.string.tab_source, song.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val sessions = log.filter { it.songId == song.id }
        if (sessions.isNotEmpty()) {
            Text(
                stringResource(R.string.practice_summary, sessions.size, sessions.sumOf { it.seconds } / 60, sessions.maxOf { it.bestTempo }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        song.patterns.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name, style = MaterialTheme.typography.labelLarge)
                StrumRow(p)
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

/** Opens a web search in the browser. The app itself downloads nothing. */
@Composable
private fun FindOnline(song: Song) {
    val context = LocalContext.current
    val query = URLEncoder.encode("${song.artist} ${song.title} guitar tab", "UTF-8")
    androidx.compose.material3.TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://duckduckgo.com/?q=$query".toUri())) }
    }) { Text(stringResource(R.string.find_tabs_browser)) }
}

@Composable
private fun Controls(
    song: Song, playing: Playing?, options: PlayOptions,
    onTempo: (Int) -> Unit, onLoop: (Int?) -> Unit, onTrainer: (Boolean) -> Unit, onPlay: () -> Unit, onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (playing == null) {
                Button(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.play_along)) }
                val tempoLabel = if (options.trainer) stringResource(R.string.speed_up) else "${options.tempoPercent} %"
                Choice(tempoLabel, listOf(50, 70, 85, 100).map { "$it %" } + stringResource(R.string.speed_up)) { i ->
                    if (i < 4) { onTrainer(false); onTempo(listOf(50, 70, 85, 100)[i]) } else onTrainer(true)
                }
                if (song.sections.size > 1) {
                    val names = listOf(stringResource(R.string.whole_song)) + song.sections.map { stringResource(R.string.loop_section, it.name) }
                    Choice(names[(options.loopSection ?: -1) + 1], names) { i -> onLoop(if (i == 0) null else i - 1) }
                }
            } else {
                Button(onClick = onStop) { Icon(Icons.Filled.Stop, null); Text(stringResource(R.string.stop)) }
                Text(
                    if (playing.bar < 0) stringResource(R.string.count_in) else "${playing.bpm} BPM · ${stringResource(R.string.bar_n, playing.bar + 1)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun Choice(label: String, items: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, item -> DropdownMenuItem(text = { Text(item) }, onClick = { open = false; onPick(i) }) }
        }
    }
}
