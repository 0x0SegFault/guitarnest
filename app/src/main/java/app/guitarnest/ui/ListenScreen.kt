package app.guitarnest.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.guitarnest.R
import app.guitarnest.audio.CaptureService
import app.guitarnest.tab.Level

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenScreen(onMade: (String, String?) -> Unit, modifier: Modifier = Modifier, vm: ListenViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val made by vm.made.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(Level.INTERMEDIATE) }
    val aiReady = androidx.compose.runtime.remember { vm.aiConfigured() }
    var refine by rememberSaveable { mutableStateOf(aiReady) }
    var identify by rememberSaveable { mutableStateOf(vm.identify) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) } // "mic" or "apps" while asking for the microphone

    LaunchedEffect(made) { made?.let { onMade(it.id, (state as? ListenState.Failed)?.message); vm.reset() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val fileName = displayName(context, uri)
        if (title.isBlank() && !identify) title = fileName
        vm.fromFile(uri, title, fileName, level, refine)
    }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        CaptureService.ready = { p -> if (p != null) vm.startCapture(p) }
        ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java)
            .putExtra(CaptureService.EXTRA_CODE, result.resultCode).putExtra(CaptureService.EXTRA_DATA, data))
    }
    fun startApps() = projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (pending == "apps") startApps() else vm.startMicrophone() }
        pending = null
    }
    fun withMic(kind: String, action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { pending = kind; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    fun stopCapture() = context.stopService(Intent(context, CaptureService::class.java))

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.listen_help), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.title)) },
            enabled = state is ListenState.Idle || state is ListenState.Failed || state is ListenState.Recording)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Level.entries.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(levelLabel(l)) }) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (aiReady) R.string.improve_ai else R.string.improve_ai_off), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = refine && aiReady, onCheckedChange = { refine = it }, enabled = aiReady)
        }
        if (vm.identify || app.guitarnest.listen.AcoustId.available) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.identify), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.identify_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = identify, onCheckedChange = { identify = it; vm.identify = it })
        }

        when (val s = state) {
            is ListenState.Recording -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.recording), style = MaterialTheme.typography.titleMedium)
                    Text("%d:%02d".format(s.seconds.toInt() / 60, s.seconds.toInt() % 60), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    LinearProgressIndicator(progress = { (s.level * 4).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    if (s.fromApps && s.seconds > 5 && s.level < 0.0005f) Text(stringResource(R.string.capture_silent), color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.stopAndMake(title, level, refine && aiReady); if (s.fromApps) stopCapture() }) { Text(stringResource(R.string.stop_make)) }
                        OutlinedButton(onClick = { vm.cancelRecording(); if (s.fromApps) stopCapture() }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
            is ListenState.Working -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(when (s.step) {
                    Step.READING -> R.string.step_reading
                    Step.LISTENING -> R.string.step_listening
                    Step.IDENTIFYING -> R.string.step_identifying
                    Step.ARRANGING -> R.string.step_arranging
                    Step.ASKING_AI -> R.string.step_asking
                }), style = MaterialTheme.typography.titleMedium)
                if (s.step == Step.ARRANGING || s.step == Step.ASKING_AI || s.step == Step.IDENTIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                if (s is ListenState.Failed) Text(s.message, color = MaterialTheme.colorScheme.error)
                Source(Icons.Filled.AudioFile, R.string.source_file, R.string.listen_files_note) { picker.launch(arrayOf("audio/*")) }
                Source(Icons.Filled.Mic, R.string.source_record, R.string.listen_record_note) { withMic("mic") { vm.startMicrophone() } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Source(Icons.Filled.PhoneAndroid, R.string.source_apps, R.string.listen_apps_note) { withMic("apps") { startApps() } }
                }
            }
        }
        Text(stringResource(R.string.listen_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Source(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, note: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0).substringBeforeLast('.') else null
    }
}.getOrNull() ?: ""
