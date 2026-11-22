package app.guitarnest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.guitarnest.R
import app.guitarnest.ai.AiTask
import app.guitarnest.ai.SongInfo
import app.guitarnest.data.AiConnection
import app.guitarnest.tab.Level
import app.guitarnest.tab.Song

/** Common OpenAI-compatible endpoints. Home-network addresses are examples to edit. */
private val PRESETS = listOf(
    "Ollama" to "http://192.168.1.10:11434/v1",
    "LM Studio" to "http://192.168.1.10:1234/v1",
    "LiteLLM" to "https://litellm.example.org/v1",
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "Mistral" to "https://api.mistral.ai/v1",
    "OpenAI" to "https://api.openai.com/v1",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConnectionSheet(current: AiConnection, job: Job<String>, onTest: (AiConnection) -> Unit, onSave: (AiConnection) -> Unit, onClear: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(current.baseUrl) }
    var model by rememberSaveable { mutableStateOf(current.model) }
    var key by rememberSaveable { mutableStateOf(current.apiKey) }
    val connection = AiConnection(url, model, key)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ai_connection), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.ai_connection_help), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRESETS.forEach { (name, address) -> FilterChip(selected = url == address, onClick = { url = address }, label = { Text(name) }) }
        }
        OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.ai_address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        if (connection.unencrypted) Text(stringResource(R.string.ai_http_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(model, { model = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.ai_model)) },
            placeholder = { Text("llama3.1, mistral-small, gpt-4o-mini…") })
        OutlinedTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.ai_key)) },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        JobStatus(job, ok = job.result?.let { stringResource(R.string.ai_test_ok, it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTest(connection) }, enabled = connection.configured && !job.running) { Text(stringResource(R.string.ai_test)) }
            Button(onClick = { onSave(connection) }, enabled = connection.configured) { Text(stringResource(R.string.save)) }
            if (current.configured) TextButton(onClick = { url = ""; model = ""; key = ""; onClear() }) { Text(stringResource(R.string.ai_remove)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskAiSheet(song: Song?, connection: AiConnection, job: Job<String>, onSetup: () -> Unit, onAsk: (AiTask, Level, String) -> Unit) {
    val tasks = if (song == null) listOf(AiTask.WRITE) else listOf(AiTask.ARRANGE, AiTask.STRUMMING)
    var task by rememberSaveable { mutableStateOf(tasks.first()) }
    var level by rememberSaveable { mutableStateOf(song?.level ?: Level.BEGINNER) }
    var request by rememberSaveable { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (song == null) R.string.ai_new_tab else R.string.ask_ai), style = MaterialTheme.typography.titleLarge)
        if (!connection.configured) {
            Text(stringResource(R.string.ai_not_set), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onSetup) { Text(stringResource(R.string.ai_connection)) }
            return@Column
        }
        if (tasks.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = task == AiTask.ARRANGE, onClick = { task = AiTask.ARRANGE }, label = { Text(stringResource(R.string.ai_arrange)) })
                FilterChip(selected = task == AiTask.STRUMMING, onClick = { task = AiTask.STRUMMING }, label = { Text(stringResource(R.string.ai_strumming)) })
            }
        }
        if (task != AiTask.STRUMMING) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Level.entries.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(levelLabel(l)) }) }
            }
        }
        OutlinedTextField(request, { request = it }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.ai_request)) },
            placeholder = { Text(stringResource(if (song == null) R.string.ai_request_hint_new else R.string.ai_request_hint)) })
        Text(stringResource(R.string.ai_sends, connection.model, hostOf(connection.baseUrl)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        JobStatus(job, ok = null)
        Button(onClick = { onAsk(task, level, request) }, enabled = !job.running && (song != null || request.isNotBlank())) { Text(stringResource(R.string.ai_make)) }
    }
}

@Composable
fun SearchSheet(job: Job<List<SongInfo>>, onSearch: (String) -> Unit, onPick: (SongInfo) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.find_song), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.find_song_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(query) }))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSearch(query) }, enabled = query.isNotBlank() && !job.running) { Text(stringResource(R.string.search)) }
            Text(stringResource(R.string.find_song_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        JobStatus(job, ok = null)
        job.result?.let { results ->
            if (results.isEmpty()) Text(stringResource(R.string.no_results))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(results) { r ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 10.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(r.artist, r.album, r.year, r.seconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "")
                            .filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun <T> JobStatus(job: Job<T>, ok: String?) {
    when {
        job.running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.working))
        }
        job.error != null -> Text(job.error, color = MaterialTheme.colorScheme.error)
        ok != null -> Text(ok, color = MaterialTheme.colorScheme.tertiary)
    }
}

private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/')
