package io.github.redon13.easyreader.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.redon13.easyreader.ui.theme.EasyColors

/** Pick book files on the device and send them to Calibre, each with its own progress bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(onBack: () -> Unit) {
    val vm: UploadViewModel = viewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.add(uris)
    }

    Scaffold(
        containerColor = EasyColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Upload to Calibre", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (items.any { it.status != UploadItem.Status.WAITING && it.status != UploadItem.Status.SENDING }) {
                        TextButton(onClick = vm::clearFinished) { Text("Clear", color = EasyColors.Accent) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EasyColors.Background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Choose one or more book files. Calibre reads the title and author from each file. " +
                        "Books Calibre already has are skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EasyColors.TextMuted,
                )
                Button(
                    onClick = { pick.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EasyColors.Accent, contentColor = EasyColors.OnAccent),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Choose files", style = MaterialTheme.typography.labelLarge) }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(items.reversed(), key = { it.id }) { item -> UploadRow(item) }
                }
            }
        }
    }
}

@Composable
private fun UploadRow(item: UploadItem) {
    Surface(color = EasyColors.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (item.status) {
                        UploadItem.Status.WAITING -> "Waiting"
                        UploadItem.Status.SENDING -> item.progress?.let { "${(it * 100).toInt()}%" } ?: "Sending"
                        UploadItem.Status.ADDED -> "Added"
                        UploadItem.Status.ALREADY_THERE -> "Skipped"
                        UploadItem.Status.FAILED -> "Failed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (item.status) {
                        UploadItem.Status.ADDED -> EasyColors.Success
                        UploadItem.Status.FAILED -> EasyColors.Error
                        else -> EasyColors.TextMuted
                    },
                    textAlign = TextAlign.End,
                )
            }
            if (item.status == UploadItem.Status.SENDING) {
                val p = item.progress
                val mod = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, color = EasyColors.Accent, trackColor = EasyColors.SurfaceHigh, modifier = mod)
                } else {
                    LinearProgressIndicator(color = EasyColors.Accent, trackColor = EasyColors.SurfaceHigh, modifier = mod)
                }
            }
            item.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted) }
        }
    }
}
