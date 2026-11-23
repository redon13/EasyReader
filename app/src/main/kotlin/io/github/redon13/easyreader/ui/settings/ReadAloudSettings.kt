package io.github.redon13.easyreader.ui.settings

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.redon13.easyreader.EasyReaderApp
import io.github.redon13.easyreader.data.GoogleTtsException
import io.github.redon13.easyreader.data.GoogleVoice
import io.github.redon13.easyreader.data.ReadAloudConfig
import io.github.redon13.easyreader.data.ReadAloudUsage
import io.github.redon13.easyreader.ui.theme.EasyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat

/** Settings → Read aloud: the Google key, the voice, and this month's usage. */
class ReadAloudSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as EasyReaderApp).container
    private val google = container.googleTts

    val config: StateFlow<ReadAloudConfig> = container.settings.readAloudConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadAloudConfig())
    val usage: StateFlow<ReadAloudUsage> = container.settings.readAloudUsage
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadAloudUsage())

    /** The app's signing fingerprint, needed when restricting the key to EasyReader. */
    val fingerprint: String? get() = google.appFingerprint
    val packageName: String get() = getApplication<Application>().packageName

    private val _voices = MutableStateFlow<List<GoogleVoice>?>(null)
    val voices: StateFlow<List<GoogleVoice>?> = _voices.asStateFlow()
    private val _voicesProblem = MutableStateFlow<String?>(null)
    val voicesProblem: StateFlow<String?> = _voicesProblem.asStateFlow()

    /** The voice whose sample is loading or playing. */
    private val _listening = MutableStateFlow<String?>(null)
    val listening: StateFlow<String?> = _listening.asStateFlow()
    private var player: MediaPlayer? = null

    private fun save(change: (ReadAloudConfig) -> ReadAloudConfig) {
        viewModelScope.launch {
            container.settings.saveReadAloudConfig(change(container.settings.readAloudConfig.first()))
        }
    }

    fun saveKey(key: String?) = save { it.copy(apiKey = key?.trim()?.takeIf(String::isNotEmpty)) }
    fun chooseVoice(name: String) = save { it.copy(voiceName = name) }
    fun setLimits(chirp: Long, wavenet: Long) = save { it.copy(chirpLimit = chirp, wavenetLimit = wavenet) }

    /** Loads Google's Chirp 3 HD voices for a language, e.g. "en-GB". */
    fun loadVoices(languageCode: String) {
        _voices.value = null
        _voicesProblem.value = null
        viewModelScope.launch {
            val key = container.settings.readAloudConfig.first().apiKey
                ?: return@launch run { _voicesProblem.value = "Add your Google key first." }
            try {
                _voices.value = google.voices(key, languageCode).filter { it.type == "Chirp 3 HD" }
            } catch (e: GoogleTtsException) {
                _voicesProblem.value = e.message
            }
        }
    }

    /** Plays a short sample of [voice] (a few dozen characters from this month's allowance). */
    fun listen(voice: GoogleVoice, languageCode: String) {
        stopListening()
        _listening.value = voice.name
        viewModelScope.launch {
            val key = container.settings.readAloudConfig.first().apiKey ?: return@launch run { _listening.value = null }
            val text = sample(languageCode, voice.shortName)
            try {
                val audio = google.synthesize(key, text, languageCode, voice.name)
                container.settings.addReadAloudUsage(chirp = true, characters = text.length.toLong())
                val file = File(getApplication<Application>().cacheDir, "voice-sample.mp3")
                withContext(Dispatchers.IO) { file.writeBytes(audio) }
                if (_listening.value != voice.name) return@launch
                player = MediaPlayer().apply {
                    setDataSource(file.path)
                    setOnCompletionListener { stopListening() }
                    prepare()
                    start()
                }
            } catch (e: GoogleTtsException) {
                _voicesProblem.value = e.message
                _listening.value = null
            } catch (e: Exception) {
                _listening.value = null
            }
        }
    }

    fun stopListening() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _listening.value = null
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private fun sample(languageCode: String, name: String) = when {
        languageCode.startsWith("hu") -> "Szia, $name vagyok. Így fogom felolvasni a könyveidet."
        else -> "Hello, I'm $name. This is how I'll read your books to you."
    }
}

private val numbers: NumberFormat = NumberFormat.getIntegerInstance(java.util.Locale.UK)

@Composable
fun ReadAloudSection(vm: ReadAloudSettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    var editingKey by remember { mutableStateOf(false) }
    var choosingVoice by remember { mutableStateOf(false) }
    var editingLimits by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Section("Read aloud") {
        // Google key
        Row(
            Modifier.fillMaxWidth().clickable { editingKey = true }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Google voice key", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (config.apiKey != null) "Added – Google voices are used" else "Not added – the phone's own voice is used",
                    style = MaterialTheme.typography.bodySmall,
                    color = EasyColors.TextMuted,
                )
            }
            Text(if (config.apiKey != null) "Change" else "Add", color = EasyColors.Accent, style = MaterialTheme.typography.bodyLarge)
        }
        Divider()
        // Voice
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = config.apiKey != null) { choosingVoice = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Voice", style = MaterialTheme.typography.bodyLarge)
                Text("${config.voiceName} · Chirp 3 HD", style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted)
            }
            if (config.apiKey != null) Text("Choose", color = EasyColors.Accent, style = MaterialTheme.typography.bodyLarge)
        }
        Divider()
        // Usage
        Column(
            Modifier.fillMaxWidth().clickable { editingLimits = true }.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row {
                Text("Used this month", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Limits", color = EasyColors.Accent, style = MaterialTheme.typography.bodyLarge)
            }
            UsageLine("Chirp 3 HD", usage.chirp, config.chirpLimit)
            UsageLine("WaveNet", usage.wavenet, config.wavenetLimit)
            Text(
                "Characters read by Google. Chirp 3 HD is used first, then WaveNet, then the phone's voice " +
                    "(also used when offline). Pausing and re-reading the same part don't count again.",
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.TextMuted,
            )
        }
        Divider()
        // Fingerprint, for restricting the key
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    val text = "Package name: ${vm.packageName}\nSHA-1 fingerprint: ${vm.fingerprint.orEmpty()}"
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("EasyReader details", text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                .padding(vertical = 12.dp),
        ) {
            Row {
                Text("Details for locking the key", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Copy", color = EasyColors.Accent, style = MaterialTheme.typography.bodyLarge)
            }
            Text("Package name: ${vm.packageName}", style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted)
            Text("SHA-1: ${vm.fingerprint ?: "unknown"}", style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted)
        }
    }

    if (editingKey) KeyDialog(config.apiKey != null, onSave = { vm.saveKey(it); editingKey = false }, onDismiss = { editingKey = false })
    if (choosingVoice) VoiceSheet(vm, config.voiceName, onDismiss = { vm.stopListening(); choosingVoice = false })
    if (editingLimits) {
        LimitsDialog(config, onSave = { c, w -> vm.setLimits(c, w); editingLimits = false }, onDismiss = { editingLimits = false })
    }
}

@Composable
private fun UsageLine(label: String, used: Long, limit: Long) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${numbers.format(used)} of ${numbers.format(limit)}", style = MaterialTheme.typography.bodyMedium, color = EasyColors.TextMuted)
        }
        LinearProgressIndicator(
            progress = { if (limit <= 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f) },
            color = EasyColors.Accent,
            trackColor = EasyColors.SurfaceHigh,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun fieldColours() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EasyColors.Accent,
    cursorColor = EasyColors.Accent,
    focusedLabelColor = EasyColors.Accent,
)

@Composable
private fun KeyDialog(hasKey: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EasyColors.Surface,
        title = { Text("Google voice key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste the key from your Google Cloud project. It's kept encrypted on this device only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EasyColors.TextMuted,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(if (hasKey) "New key" else "Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColours(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("Save", color = EasyColors.Accent) }
        },
        dismissButton = {
            Row {
                if (hasKey) TextButton(onClick = { onSave(null) }) { Text("Remove key", color = EasyColors.Error) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = EasyColors.Text) }
            }
        },
    )
}

@Composable
private fun LimitsDialog(config: ReadAloudConfig, onSave: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    var chirp by remember { mutableStateOf((config.chirpLimit / 1000).toString()) }
    var wavenet by remember { mutableStateOf((config.wavenetLimit / 1000).toString()) }
    val chirpValue = chirp.toLongOrNull()
    val wavenetValue = wavenet.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EasyColors.Surface,
        title = { Text("Monthly limits") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "In thousands of characters. Google's free allowance is 1,000 for Chirp 3 HD and 4,000 for WaveNet; " +
                        "the limits are a little lower to stay safely inside it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EasyColors.TextMuted,
                )
                OutlinedTextField(
                    value = chirp,
                    onValueChange = { chirp = it.filter(Char::isDigit) },
                    label = { Text("Chirp 3 HD (thousands)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColours(),
                )
                OutlinedTextField(
                    value = wavenet,
                    onValueChange = { wavenet = it.filter(Char::isDigit) },
                    label = { Text("WaveNet (thousands)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColours(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(chirpValue!! * 1000, wavenetValue!! * 1000) },
                enabled = chirpValue != null && wavenetValue != null,
            ) { Text("Save", color = EasyColors.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = EasyColors.Text) } },
    )
}

/** Lists Google's Chirp 3 HD voices; tap ▶ to hear one, tap the name to choose it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSheet(vm: ReadAloudSettingsViewModel, chosen: String, onDismiss: () -> Unit) {
    var language by remember { mutableStateOf("en-GB") }
    val voices by vm.voices.collectAsStateWithLifecycle()
    val problem by vm.voicesProblem.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    LaunchedEffect(language) { vm.loadVoices(language) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Choose a voice", style = MaterialTheme.typography.titleLarge)
            Text(
                "The same voice is used for every language. Press ▶ to hear a sample.",
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.TextMuted,
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en-GB" to "English (UK)", "hu-HU" to "Magyar").forEach { (code, label) ->
                    FilterChip(
                        selected = language == code,
                        onClick = { vm.stopListening(); language = code },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = EasyColors.SheetChip,
                            labelColor = EasyColors.Text,
                            selectedContainerColor = EasyColors.Accent,
                            selectedLabelColor = EasyColors.OnAccent,
                        ),
                    )
                }
            }
            problem?.let { Text(it, color = EasyColors.Error, style = MaterialTheme.typography.bodyMedium) }
            val list = voices
            if (list == null && problem == null) {
                CircularProgressIndicator(Modifier.padding(24.dp).size(28.dp), color = EasyColors.Accent)
            }
            if (list != null) {
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(list, key = { it.name }) { voice ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.chooseVoice(voice.shortName) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { if (listening == voice.name) vm.stopListening() else vm.listen(voice, language) }) {
                                if (listening == voice.name) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = EasyColors.Accent, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PlayArrow, "Listen to ${voice.shortName}", tint = EasyColors.Accent)
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(voice.shortName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    voice.gender.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EasyColors.TextMuted,
                                )
                            }
                            if (voice.shortName == chosen) Icon(Icons.Default.Check, "Chosen", tint = EasyColors.Accent)
                        }
                    }
                }
            }
        }
    }
}
