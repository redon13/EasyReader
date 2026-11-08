package io.github.redon13.easyreader.ui.settings

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.data.ReaderTheme
import io.github.redon13.easyreader.ui.browse.BrowseViewModel
import io.github.redon13.easyreader.ui.theme.Alegreya
import io.github.redon13.easyreader.ui.theme.EasyColors
import kotlin.math.roundToInt

/** Settings: Server, Reading and Library sections (docs/DESIGN.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BrowseViewModel, onBack: () -> Unit) {
    val app by vm.appSettings.collectAsStateWithLifecycle()
    val reader by vm.readerSettings.collectAsStateWithLifecycle()
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val lastSynced by vm.lastSynced.collectAsStateWithLifecycle()
    val syncError by vm.syncError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = EasyColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EasyColors.Background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ---- Server ----
                Section("Server") {
                    InfoRow("Address", vm.session.client.serverUrl.toString().removeSuffix("/"))
                    Divider()
                    InfoRow(
                        "Status",
                        if (syncError == null) "Connected" else "Not connected",
                        valueColor = if (syncError == null) EasyColors.Success else EasyColors.Error,
                    )
                    syncError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Divider()
                    LibraryRow(vm, libraries)
                    Divider()
                    InfoRow("Account", vm.session.client.username)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = vm::signOut,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) { Text("Sign out", color = EasyColors.Text) }
                }

                // ---- Reading ----
                Section("Reading") {
                    Text("Reader background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        ReaderTheme.entries.forEach { theme ->
                            val chosen = reader.theme == theme
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { vm.updateReaderSettings { it.copy(theme = theme) } },
                            ) {
                                Box(
                                    Modifier
                                        .size(60.dp)
                                        .background(Color(theme.background), CircleShape)
                                        .border(if (chosen) 3.dp else 1.dp, if (chosen) EasyColors.Accent else EasyColors.Outline, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Aa", fontFamily = Alegreya, fontSize = 20.sp, color = Color(theme.text)) }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    theme.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (chosen) EasyColors.Accent else EasyColors.TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(80.dp),
                                )
                            }
                        }
                    }
                    Divider()
                    FinishedAtRow(app.finishedAt) { v -> vm.updateAppSettings { it.copy(finishedAt = v) } }
                    Divider()
                    SwitchRow("Resume last book on launch", app.resumeLastBook) { v -> vm.updateAppSettings { it.copy(resumeLastBook = v) } }
                }

                // ---- Library ----
                Section("Library") {
                    SwitchRow("Progress bar on covers", app.progressOnCovers) { v -> vm.updateAppSettings { it.copy(progressOnCovers = v) } }
                    Divider()
                    SwitchRow(
                        "Sync every 10 minutes",
                        app.autoSync,
                        note = "While EasyReader is open. It also syncs each time it starts.",
                    ) { v -> vm.updateAppSettings { it.copy(autoSync = v) } }
                    Divider()
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = vm::sync,
                            enabled = !syncing,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EasyColors.Accent,
                                contentColor = EasyColors.OnAccent,
                                disabledContainerColor = EasyColors.SurfaceHigh,
                                disabledContentColor = EasyColors.TextMuted,
                            ),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            if (syncing) CircularProgressIndicator(Modifier.size(20.dp), color = EasyColors.TextMuted, strokeWidth = 2.dp)
                            else Text("Sync now", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            lastSynced?.let { "Last synced " + describeTime(context, it) } ?: "Not synced yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = EasyColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Divider()
                    SwitchRow(
                        "Remove finished downloads after 7 days",
                        app.cleanUpFinished,
                        note = "Unfinished books stay on the device until you remove them.",
                    ) { v -> vm.updateAppSettings { it.copy(cleanUpFinished = v) } }
                }

                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                }
                Text(
                    "EasyReader ${version.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EasyColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Surface(color = EasyColors.Surface, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() }
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = EasyColors.Divider)

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = EasyColors.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = EasyColors.TextMuted, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LibraryRow(vm: BrowseViewModel, libraries: Map<String, String>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (libraries.size > 1) Modifier.clickable { open = true } else Modifier)
                .padding(vertical = 14.dp),
        ) {
            Text("Library", style = MaterialTheme.typography.bodyLarge, color = EasyColors.TextMuted, modifier = Modifier.width(110.dp))
            Text(vm.session.libraryName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (libraries.size > 1) Text("Change", color = EasyColors.Accent, style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            libraries.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; vm.switchLibrary(id, name) })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EasyColors.OnAccent,
                checkedTrackColor = EasyColors.Accent,
                uncheckedThumbColor = EasyColors.TextMuted,
                uncheckedTrackColor = EasyColors.SurfaceHigh,
                uncheckedBorderColor = EasyColors.Outline,
            ),
        )
    }
}

/** "Mark as finished at": 80–100% in steps of 5. */
@Composable
private fun FinishedAtRow(value: Double, onChange: (Double) -> Unit) {
    var dragging by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragging >= 0) dragging else (value * 100).toFloat()
    Column(Modifier.padding(vertical = 12.dp)) {
        Row {
            Text("Mark as finished at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${shown.roundToInt()}%", style = MaterialTheme.typography.bodyLarge, color = EasyColors.Accent)
        }
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                onChange((dragging / 5).roundToInt() * 5 / 100.0)
                dragging = -1f
            },
            valueRange = 80f..100f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = EasyColors.Accent,
                activeTrackColor = EasyColors.Accent,
                inactiveTrackColor = EasyColors.SurfaceHigh,
            ),
        )
    }
}

private fun describeTime(context: android.content.Context, time: Long): String {
    val clock = DateFormat.getTimeFormat(context).format(time)
    return if (DateUtils.isToday(time)) "today at $clock" else "${DateFormat.getMediumDateFormat(context).format(time)} at $clock"
}
