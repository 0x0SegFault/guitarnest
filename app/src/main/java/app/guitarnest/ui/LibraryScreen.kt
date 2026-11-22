package app.guitarnest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.guitarnest.R
import app.guitarnest.data.PracticeEntry
import app.guitarnest.tab.Level
import app.guitarnest.tab.Song
import java.time.LocalDate

@Composable
fun LibraryScreen(songs: List<Song>, log: List<PracticeEntry>, onOpen: (Song) -> Unit, onNew: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = songs.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
    Column(modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_library)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (songs.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNew) { Text(stringResource(R.string.new_song)) }
                    OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_file)) }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { song ->
                SongRow(song, log.filter { it.songId == song.id }) { onOpen(song) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, log: List<PracticeEntry>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(song.title, style = MaterialTheme.typography.titleMedium)
        val details = listOfNotNull(
            song.artist.takeIf { it.isNotBlank() },
            levelLabel(song.level),
            song.tuningNotes()?.let { t -> if (t.id == "standard") null else t.names.joinToString("") { it.dropLast(1) } },
            if (song.capo > 0) stringResource(R.string.capo_n, song.capo) else null,
            "${song.tempo} BPM",
        ).joinToString(" · ")
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        log.maxByOrNull { it.day }?.let { last ->
            val days = (LocalDate.now().toEpochDay() - last.day).toInt()
            Text(
                if (days == 0) stringResource(R.string.practised_today) else pluralStringResource(R.plurals.practised_days_ago, days, days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
fun levelLabel(level: Level): String = stringResource(
    when (level) {
        Level.BEGINNER -> R.string.level_beginner
        Level.INTERMEDIATE -> R.string.level_intermediate
        Level.ADVANCED -> R.string.level_advanced
    }
)
