package app.guitarnest.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.guitarnest.R
import app.guitarnest.about.Credits

/** Everything GuitarNest is built with (same list as CREDITS.md). Tap an entry to open its page. */
@Composable
fun CreditsSheet() {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.credits_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Credits.SECTIONS.forEach { section ->
            Text(heading(section.key), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            section.items.forEach { c ->
                Column(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, c.url.toUri())) } }.padding(vertical = 4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(c.use, style = MaterialTheme.typography.bodySmall)
                    Text(c.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun heading(key: String): String = stringResource(when (key) {
    "credits_in_app" -> R.string.credits_in_app
    "credits_services" -> R.string.credits_services
    "credits_tools" -> R.string.credits_tools
    "credits_methods" -> R.string.credits_methods
    else -> R.string.credits_music
})
