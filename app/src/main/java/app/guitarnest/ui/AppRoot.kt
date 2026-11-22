package app.guitarnest.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.guitarnest.BuildConfig
import app.guitarnest.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var about by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { about = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about))
                    }
                },
            )
        },
    ) { padding ->
        TunerScreen(Modifier.fillMaxSize().padding(padding))
    }
    if (about) {
        ModalBottomSheet(onDismissRequest = { about = false }) { AboutSheet() }
    }
}

@Composable
private fun AboutSheet() {
    val context = LocalContext.current
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.permissions), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.permission_mic), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.permission_none_else), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = {
            // Some de-Googled phones have no browser.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) }
        }) { Text(stringResource(R.string.source_code)) }
    }
}

private const val SOURCE_URL = "https://github.com/0x0SegFault/guitarnest"
