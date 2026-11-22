package app.guitarnest.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.guitarnest.R
import java.io.File
import java.time.LocalDate

/** Backups: one encrypted file with all songs and the practice history. Where it goes is the player's choice. */
@Composable
fun BackupSheet(vm: SongsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var restoreFile by remember { mutableStateOf<ByteArray?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    val name = "guitarnest-${LocalDate.now()}.gnbackup"

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val blob = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(blob) } }
            .onSuccess { vm.showMessage(context.getString(R.string.backup_saved)); onDone() }
            .onFailure { vm.showMessage(it.message) }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        restoreFile = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (restoreFile == null) vm.showMessage(context.getString(R.string.import_failed))
    }
    val valid = password.length >= 8 && password == repeat

    fun make(then: (ByteArray) -> Unit) {
        working = true
        vm.createBackup(password.toCharArray()) { blob -> working = false; then(blob) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.backup_password)) },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(repeat, { repeat = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.backup_repeat)) },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = repeat.isNotEmpty() && repeat != password)
        Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = valid && !working, onClick = { make { blob -> pending = blob; saver.launch(name) } }) { Text(stringResource(R.string.backup_to_file)) }
            OutlinedButton(enabled = valid && !working, onClick = {
                make { blob ->
                    val dir = File(context.cacheDir, "backups").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val file = File(dir, name).apply { writeBytes(blob) }
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".backups", file)
                    val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_to_app)))
                    onDone()
                }
            }) { Text(stringResource(R.string.backup_to_app)) }
        }
        if (working) Text(stringResource(R.string.working))

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.restore_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (restoreFile == null) {
            OutlinedButton(onClick = { opener.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.restore_choose)) }
        } else {
            OutlinedTextField(restorePassword, { restorePassword = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.backup_password)) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(enabled = restorePassword.isNotEmpty() && !working, onClick = {
                working = true
                vm.restoreBackup(restoreFile!!, restorePassword.toCharArray()) { error -> working = false; restoreError = error; if (error == null) onDone() }
            }) { Text(stringResource(R.string.restore_now)) }
            restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
