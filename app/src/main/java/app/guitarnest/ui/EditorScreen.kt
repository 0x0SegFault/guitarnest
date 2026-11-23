package app.guitarnest.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitarnest.R
import app.guitarnest.practice.PRESET_PATTERNS
import app.guitarnest.bridge.Bridge
import app.guitarnest.bridge.SectionDraft as BridgeDraft
import app.guitarnest.tab.Level
import app.guitarnest.tab.Song
import app.guitarnest.tab.StrumPattern
import app.guitarnest.tab.TextTab
import app.guitarnest.tuner.Tuning

/** Editable copy of a section: the bars are kept as text tab while editing. */
data class SectionDraft(val name: String, val repeat: Int, val strum: String?, val text: String)

/** The editor's state. [build] turns it into a song, or returns the problems. */
class SongDraft(initial: Song) {
    var song by mutableStateOf(initial)
    val patterns: SnapshotStateList<StrumPattern> = mutableStateListOf<StrumPattern>().apply { addAll(initial.patterns) }
    val sections: SnapshotStateList<SectionDraft> = mutableStateListOf<SectionDraft>().apply {
        addAll(initial.sections.map { SectionDraft(it.name, it.repeat, it.strum, TextTab.sectionText(it, initial.beatsPerBar)) })
    }

    fun build(): Result<Song> {
        val r = Bridge.build(song, song.title, song.artist, song.source, song.level, song.tuning, song.capo, song.tempo, song.beatsPerBar,
            patterns.toList(), sections.map { BridgeDraft(it.name, it.repeat, it.strum, it.text) }, song.notes)
        return r.song?.let { Result.success(it) } ?: Result.failure(Exception(r.error))
    }
}

@Composable
fun rememberDraft(song: Song?): SongDraft = remember(song?.id, song?.title) { SongDraft(song ?: Song(title = "")) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(draft: SongDraft, error: String?, modifier: Modifier = Modifier) {
    val s = draft.song
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Field(R.string.title, s.title) { draft.song = s.copy(title = it) }
        Field(R.string.artist, s.artist) { draft.song = s.copy(artist = it) }
        Field(R.string.tab_by, s.source) { draft.song = s.copy(source = it) }

        Label(R.string.level)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Level.entries.forEach { l -> FilterChip(selected = s.level == l, onClick = { draft.song = s.copy(level = l) }, label = { Text(levelLabel(l)) }) }
        }
        Label(R.string.tuning)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tuning.PRESETS.forEach { t ->
                FilterChip(selected = s.tuning == t.id, onClick = { draft.song = s.copy(tuning = t.id) }, label = { Text(t.names.joinToString("") { it.dropLast(1) }) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Number(R.string.capo, s.capo, Modifier.weight(1f)) { draft.song = s.copy(capo = it) }
            Number(R.string.tempo_bpm, s.tempo, Modifier.weight(1f)) { draft.song = s.copy(tempo = it) }
            Number(R.string.beats_per_bar, s.beatsPerBar, Modifier.weight(1f)) { draft.song = s.copy(beatsPerBar = it) }
        }

        Label(R.string.strumming_patterns)
        draft.patterns.forEachIndexed { i, p ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p.name, { draft.patterns[i] = p.copy(name = it) }, Modifier.width(110.dp), singleLine = true, label = { Text(stringResource(R.string.name)) })
                    val allowed = if (p.picking) "Bb123456-" else "DURx-"
                    OutlinedTextField(
                        p.steps, { v -> draft.patterns[i] = p.copy(steps = (if (p.picking) v else v.uppercase().replace('X', 'x')).filter { it in allowed }) },
                        Modifier.weight(1f), singleLine = true, label = { Text(if (p.picking) "B b 1-6 -" else "D U R x -") }, textStyle = mono,
                        keyboardOptions = KeyboardOptions(capitalization = if (p.picking) KeyboardCapitalization.None else KeyboardCapitalization.Characters),
                    )
                    IconButton(onClick = { draft.patterns.removeAt(i) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !p.picking, onClick = { draft.patterns[i] = p.copy(kind = StrumPattern.STRUM, steps = p.steps.filter { it in "DURx-" }) },
                        label = { Text(stringResource(R.string.pattern_strum)) })
                    FilterChip(selected = p.picking, onClick = { draft.patterns[i] = p.copy(kind = StrumPattern.PICK, steps = p.steps.filter { it in "Bb123456-" }) },
                        label = { Text(stringResource(R.string.pattern_pick)) })
                }
                Text(stringResource(if (p.picking) R.string.pattern_help_pick else R.string.pattern_help_strum),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRESET_PATTERNS.filter { pp -> draft.patterns.none { it.id == pp.id } }.forEach { pp ->
                OutlinedButton(onClick = { draft.patterns += pp }) { Text("+ ${pp.name}") }
            }
        }

        Label(R.string.sections)
        Text(stringResource(R.string.section_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        draft.sections.forEachIndexed { i, d ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(d.name, { draft.sections[i] = d.copy(name = it) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.name)) })
                        Number(R.string.repeat, d.repeat, Modifier.width(90.dp)) { draft.sections[i] = d.copy(repeat = it) }
                        IconButton(onClick = { draft.sections.removeAt(i) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = d.strum == null, onClick = { draft.sections[i] = d.copy(strum = null) }, label = { Text(stringResource(R.string.none)) })
                        draft.patterns.forEach { p -> FilterChip(selected = d.strum == p.id, onClick = { draft.sections[i] = d.copy(strum = p.id) }, label = { Text(p.name) }) }
                    }
                    // Tab lines must not wrap: the field is as wide as the longest line and scrolls sideways.
                    val longest = d.text.lines().maxOfOrNull { it.length } ?: 0
                    val problem = TextTab.parseSection(d.text, s.beatsPerBar).exceptionOrNull()?.message
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        OutlinedTextField(d.text, { draft.sections[i] = d.copy(text = it) },
                            Modifier.widthIn(min = 280.dp).width(((longest + 4) * 8).dp), textStyle = mono, minLines = 3,
                            isError = problem != null)
                    }
                    if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = { draft.sections += SectionDraft("Section ${draft.sections.size + 1}", 1, draft.patterns.firstOrNull()?.id, "| G | C | D | G |") }) {
            Text(stringResource(R.string.add_section))
        }
        Field(R.string.notes, s.notes, singleLine = false) { draft.song = s.copy(notes = it) }
    }
}

private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
private fun Label(id: Int) = Text(stringResource(id), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Field(label: Int, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = singleLine, label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
}

@Composable
private fun Number(label: Int, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value.toString(), { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let(onChange) ?: onChange(0) }, modifier,
        singleLine = true, label = { Text(stringResource(label), maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
