package app.guitarnest.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.guitarnest.BuildConfig
import app.guitarnest.R
import app.guitarnest.data.SongPrinter
import app.guitarnest.feedback.CrashLog
import app.guitarnest.tab.Song
import app.guitarnest.tab.SongCodec

private enum class Tab { SONGS, METRONOME, TUNER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: SongsViewModel = viewModel()) {
    val context = LocalContext.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val metronome by vm.metronome.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.SONGS) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // song id, or "" for a new song
    var listening by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) } // "ai", "ask", "search"
    var seed by remember { mutableStateOf<Song?>(null) }
    // The last crash, kept on this phone only; the player decides whether to report it.
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    // If the player turned on automatic crash reports, send the last crash (anonymised) once, at start.
    LaunchedEffect(Unit) {
        val last = crash ?: return@LaunchedEffect
        if (vm.sendCrashAutomatically(last)) crash = null
    }
    val ai by vm.ai.collectAsStateWithLifecycle()
    val aiJob by vm.aiJob.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val open = songs.firstOrNull { it.id == openId }
    val draft = rememberDraft(if (editing.isNullOrEmpty()) seed else songs.firstOrNull { it.id == editing })

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.showMessage(null) } }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
        if (bytes == null) vm.showMessage(context.getString(R.string.import_failed)) else vm.import(bytes, name) { openId = it.id; tab = Tab.SONGS }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val song = open ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(SongCodec.encode(song).toByteArray()) } }
            .onFailure { vm.showMessage(it.message) }
    }

    BackHandler(enabled = editing != null || openId != null || listening) {
        when {
            listening -> listening = false
            editing != null -> { editing = null; editorError = null }
            else -> { vm.stop(); openId = null }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            listening -> stringResource(R.string.listen_title)
                            editing != null -> stringResource(if (editing == "") R.string.new_song else R.string.edit_song)
                            open != null && tab == Tab.SONGS -> open.title
                            else -> stringResource(R.string.app_name)
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (listening || editing != null || (open != null && tab == Tab.SONGS)) {
                        IconButton(onClick = { if (listening) listening = false else if (editing != null) { editing = null; editorError = null } else { vm.stop(); openId = null } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    when {
                        listening -> {}
                        editing != null -> TextButton(onClick = {
                            draft.build().onSuccess { song -> vm.save(song) { saved -> openId = saved.id; editing = null; editorError = null } }
                                .onFailure { editorError = it.message }
                        }) { Text(stringResource(R.string.save)) }
                        open != null && tab == Tab.SONGS -> SongMenu(
                            onAskAi = { vm.stop(); vm.resetJobs(); sheet = "ask" },
                            onEdit = { vm.stop(); editing = open.id },
                            onPrint = { SongPrinter.print(context, open) },
                            onExport = { exporter.launch(open.title.replace(Regex("[^\\w ]"), "") + ".gnt") },
                            onDelete = { confirmDelete = true },
                        )
                        tab == Tab.SONGS -> {
                            IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.LibraryMusic, stringResource(R.string.import_file)) }
                            NewSongMenu(
                                onListen = { listening = true },
                                onWrite = { seed = null; editing = "" },
                                onAi = { vm.resetJobs(); sheet = "ask" },
                                onSearch = { vm.resetJobs(); sheet = "search" },
                            )
                            SettingsMenu(onAi = { vm.resetJobs(); sheet = "ai" }, onBackup = { sheet = "backup" }, onReport = { sheet = "report" },
                                onMyReports = { sheet = "my_reports" }, onAbout = { about = true })
                        }
                        else -> IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.about)) }
                    }
                },
            )
        },
        bottomBar = {
            if (editing == null && !listening) {
                NavigationBar {
                    NavigationBarItem(tab == Tab.SONGS, { tab = Tab.SONGS }, { Icon(Icons.Filled.LibraryMusic, null) }, label = { Text(stringResource(R.string.songs)) })
                    NavigationBarItem(tab == Tab.METRONOME, { tab = Tab.METRONOME }, { Icon(Icons.Filled.Timer, null) }, label = { Text(stringResource(R.string.metronome)) })
                    NavigationBarItem(tab == Tab.TUNER, { vm.stop(); tab = Tab.TUNER }, { Icon(Icons.Filled.GraphicEq, null) }, label = { Text(stringResource(R.string.tuner)) })
                }
            }
        },
    ) { padding ->
        val m = Modifier.fillMaxSize().padding(padding)
        when {
            listening -> ListenScreen(onMade = { id, notice -> vm.refresh(); listening = false; tab = Tab.SONGS; openId = id; notice?.let(vm::showMessage) }, modifier = m)
            editing != null -> EditorScreen(draft, editorError, m)
            tab == Tab.SONGS && open != null -> SongScreen(open, playing, log, vm, m)
            tab == Tab.SONGS -> Column(m) {
                if (crash != null) CrashBanner(onReport = { sheet = "report" }, onDismiss = { CrashLog.clear(context); crash = null })
                LibraryScreen(songs, log, { openId = it.id }, { seed = null; editing = "" }, { importer.launch(arrayOf("*/*")) }, Modifier.weight(1f))
            }
            tab == Tab.METRONOME -> MetronomeScreen(metronome, playing, vm, m)
            else -> TunerScreen(m)
        }
    }

    if (confirmDelete && open != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_song_title, open.title)) },
            text = { Text(stringResource(R.string.delete_song_text)) },
            confirmButton = { TextButton(onClick = { vm.stop(); vm.delete(open.id); openId = null; confirmDelete = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (about) ModalBottomSheet(onDismissRequest = { about = false }) { AboutSheet(onCredits = { about = false; sheet = "credits" }) }
    when (sheet) {
        "ai" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AiConnectionSheet(ai, aiJob, vm::testAi, onSave = { vm.saveAi(it); sheet = null }, onClear = vm::clearAi, onOpenRouter = vm::signInOpenRouter)
        }
        "ask" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AskAiSheet(if (tab == Tab.SONGS) open else null, ai, aiJob, onSetup = { vm.resetJobs(); sheet = "ai" }) { task, level, request ->
                vm.askAi(task, level, request, if (tab == Tab.SONGS) open else null) { made -> sheet = null; openId = made.id; tab = Tab.SONGS }
            }
        }
        "backup" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            BackupSheet(vm) { sheet = null }
        }
        "report" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ReportSheet(crash) { message -> sheet = null; crash = CrashLog.read(context); message?.let(vm::showMessage) }
        }
        "credits" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CreditsSheet()
        }
        "my_reports" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            MyReportsSheet()
        }
        "search" -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SearchSheet(search, vm::searchSongs) { info ->
                seed = Song(title = info.title, artist = info.artist, notes = listOf(info.album, info.year).filter { it.isNotBlank() }.joinToString(", "))
                sheet = null
                editing = ""
            }
        }
    }
}

@Composable
private fun SongMenu(onAskAi: () -> Unit, onEdit: () -> Unit, onPrint: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.ask_ai)) }, onClick = { open = false; onAskAi() })
        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { open = false; onEdit() })
        DropdownMenuItem(text = { Text(stringResource(R.string.print)) }, onClick = { open = false; onPrint() })
        DropdownMenuItem(text = { Text(stringResource(R.string.export_file)) }, onClick = { open = false; onExport() })
        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { open = false; onDelete() })
    }
}

@Composable
private fun CrashBanner(onReport: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.crash_banner), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReport) { Text(stringResource(R.string.report_title)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun NewSongMenu(onListen: () -> Unit, onWrite: () -> Unit, onAi: () -> Unit, onSearch: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.Add, stringResource(R.string.new_song)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.listen_title)) }, onClick = { open = false; onListen() })
        DropdownMenuItem(text = { Text(stringResource(R.string.write_tab)) }, onClick = { open = false; onWrite() })
        DropdownMenuItem(text = { Text(stringResource(R.string.find_song)) }, onClick = { open = false; onSearch() })
        DropdownMenuItem(text = { Text(stringResource(R.string.ai_new_tab)) }, onClick = { open = false; onAi() })
    }
}

@Composable
private fun SettingsMenu(onAi: () -> Unit, onBackup: () -> Unit, onReport: () -> Unit, onMyReports: () -> Unit, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.ai_connection)) }, onClick = { open = false; onAi() })
        DropdownMenuItem(text = { Text(stringResource(R.string.backup_title)) }, onClick = { open = false; onBackup() })
        DropdownMenuItem(text = { Text(stringResource(R.string.report_title)) }, onClick = { open = false; onReport() })
        DropdownMenuItem(text = { Text(stringResource(R.string.my_reports)) }, onClick = { open = false; onMyReports() })
        DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { open = false; onAbout() })
    }
}

@Composable
private fun AboutSheet(onCredits: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.permissions), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.permission_mic), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.permission_apps), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.permission_none_else), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.copyright_note), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCredits) { Text(stringResource(R.string.credits_title)) }
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, OPEN_MUSIC_URL.toUri())) }
        }) { Text(stringResource(R.string.open_music)) }
        TextButton(onClick = {
            // Some de-Googled phones have no browser.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) }
        }) { Text(stringResource(R.string.source_code)) }
    }
}

private const val SOURCE_URL = "https://github.com/0x0SegFault/guitarnest"
private const val OPEN_MUSIC_URL = "https://github.com/0x0SegFault/guitarnest/blob/main/docs/OPEN_MUSIC.md"
