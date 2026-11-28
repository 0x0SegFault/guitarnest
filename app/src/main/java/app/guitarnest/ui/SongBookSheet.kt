package app.guitarnest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.guitarnest.R
import app.guitarnest.songbook.BookSong
import app.guitarnest.songbook.SongBook
import app.guitarnest.tab.Level

/** Public-domain songs arranged for GuitarNest. Pick a level, then a song: it is added to your songs. */
@Composable
fun SongBookSheet(onPick: (BookSong, Level) -> Unit) {
    var level by rememberSaveable { mutableStateOf(Level.BEGINNER) }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.song_book), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.song_book_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Level.entries.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(levelLabel(l)) }) }
        }
        Text(stringResource(when (level) {
            Level.BEGINNER -> R.string.song_book_beginner
            Level.INTERMEDIATE -> R.string.song_book_intermediate
            Level.ADVANCED -> R.string.song_book_advanced
        }), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(SongBook.songs()) { book ->
                Column(Modifier.fillMaxWidth().clickable { onPick(book, level) }.padding(vertical = 10.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    Text(book.origin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}
