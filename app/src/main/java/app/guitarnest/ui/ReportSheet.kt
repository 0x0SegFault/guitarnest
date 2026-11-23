package app.guitarnest.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.guitarnest.BuildConfig
import app.guitarnest.R
import app.guitarnest.feedback.CrashLog
import app.guitarnest.feedback.ReportStore
import app.guitarnest.net.ReportClient
import app.guitarnest.report.Reports
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Report a problem or an idea. Three ways: anonymously to GuitarNest's report service, on GitHub
 * (needs an account; public), or as text with any app. The exact issue is shown before sending.
 */
@Composable
fun ReportSheet(crash: String?, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ReportStore(context) }
    var kind by remember { mutableStateOf("bug") }
    var title by remember { mutableStateOf(if (crash != null) Reports.crashTitle(crash) else "") }
    var details by remember { mutableStateOf("") }
    var withDevice by remember { mutableStateOf(true) }
    var withCrash by remember { mutableStateOf(crash != null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val version = BuildConfig.VERSION_NAME
    val device = if (withDevice) CrashLog.deviceText() else ""
    // Crashes are always anonymised: error types and code lines only, no messages.
    val cleanCrash = crash?.let { Reports.anonymizeCrash(it) }
    val fullDetails = details.trim() + if (withCrash && cleanCrash != null) "\n\nCrash (anonymised):\n$cleanCrash" else ""
    val valid = title.trim().length >= 5 && fullDetails.trim().length >= 10

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == "bug", onClick = { kind = "bug" }, label = { Text(stringResource(R.string.report_problem)) })
            FilterChip(selected = kind == "feature", onClick = { kind = "feature" }, label = { Text(stringResource(R.string.report_idea)) })
        }
        OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.report_short)) })
        OutlinedTextField(details, { details = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 4, label = { Text(stringResource(R.string.report_details)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.report_with_device, "GuitarNest $version · ${CrashLog.deviceText()}"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(withDevice, { withDevice = it })
        }
        if (crash != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_with_crash), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(withCrash, { withCrash = it })
            }
        }
        // The exact issue that will be created.
        Text(stringResource(R.string.report_preview), style = MaterialTheme.typography.titleSmall)
        Text(
            Reports.preview(kind, title, fullDetails, version, device),
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(10.dp),
        )
        Text(stringResource(if (Reports.SERVICE_URL != null) R.string.report_privacy else R.string.report_not_ready),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (Reports.SERVICE_URL != null) {
            Button(enabled = valid && !sending, onClick = {
                sending = true
                val code = ReportStore.newCode()
                scope.launch {
                    runCatching { ReportClient.send(kind, title.trim(), fullDetails, version, device, code) }
                        .onSuccess {
                            store.add(Reports.Sent(code, kind, title.trim(), LocalDate.now().toEpochDay()))
                            if (withCrash) CrashLog.clear(context)
                            onDone(context.getString(R.string.report_sent))
                        }
                        .onFailure { error = it.message; sending = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (sending) R.string.working else R.string.report_send)) }
        }
        OutlinedButton(enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Reports.githubUrl(kind, title, fullDetails, version, device).toUri())) }
            if (withCrash) CrashLog.clear(context)
            onDone(null)
        }) { Text(stringResource(R.string.report_github)) }
        OutlinedButton(enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Reports.shareText(kind, title, fullDetails, version, device))
            context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share)))
            if (withCrash) CrashLog.clear(context)
            onDone(null)
        }) { Text(stringResource(R.string.report_share)) }
    }
}

/** Reports sent from this phone, with their status. The status is asked only while this list is open. */
@Composable
fun MyReportsSheet() {
    val context = LocalContext.current
    val store = remember { ReportStore(context) }
    val sent = remember { store.sent() }
    val status = remember { mutableStateMapOf<String, Reports.Status>() }
    var auto by remember { mutableStateOf(store.autoCrash) }
    LaunchedEffect(Unit) { sent.forEach { s -> status[s.code] = ReportClient.status(s.code) } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.my_reports), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.my_reports_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sent.isEmpty()) Text(stringResource(R.string.my_reports_empty))
        sent.forEach { s ->
            val st = status[s.code]
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text((if (s.kind == "bug") "🐞 " else "💡 ") + s.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    LocalDate.ofEpochDay(s.day).toString() + " · " + stringResource(when (st?.status) {
                        null -> R.string.working
                        "received" -> R.string.status_received
                        "published" -> R.string.status_published
                        "closed" -> R.string.status_closed
                        "declined" -> R.string.status_declined
                        "offline" -> R.string.status_offline
                        else -> R.string.status_sent
                    }),
                    style = MaterialTheme.typography.bodySmall,
                )
                st?.public?.let { link ->
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) } }) {
                        Text(stringResource(R.string.status_open_public))
                    }
                }
            }
            HorizontalDivider()
        }
        Row(Modifier.fillMaxWidth().clickable { auto = !auto; store.autoCrash = auto }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_crash), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.auto_crash_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(auto, { auto = it; store.autoCrash = it })
        }
    }
}
