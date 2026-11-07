package io.github.redon13.easyreader.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.redon13.easyreader.AppContainer
import io.github.redon13.easyreader.R
import io.github.redon13.easyreader.data.SessionState
import io.github.redon13.easyreader.ui.theme.EasyColors

@Composable
fun LoginScreen(container: AppContainer, state: SessionState.SignedOut) {
    val vm = viewModel { LoginViewModel(container.sessions, state) }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(EasyColors.Background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon()
            Text("EasyReader", style = MaterialTheme.typography.displaySmall)
            Text(
                "Connect to your Calibre library",
                style = MaterialTheme.typography.bodyLarge,
                color = EasyColors.TextMuted,
            )
            Spacer(Modifier.height(8.dp))

            Field(
                value = vm.address,
                onChange = { vm.address = it },
                label = "Server address",
                placeholder = "http://my-pc:8080",
                keyboardType = KeyboardType.Uri,
            )
            Field(
                value = vm.username,
                onChange = { vm.username = it },
                label = "Username",
            )
            Field(
                value = vm.password,
                onChange = { vm.password = it },
                label = "Password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onDone = vm::connect,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show", color = EasyColors.Accent)
                    }
                },
            )

            vm.error?.let {
                Text(
                    it,
                    color = EasyColors.Error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = vm::connect,
                enabled = vm.canConnect,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EasyColors.Accent,
                    contentColor = EasyColors.OnAccent,
                    disabledContainerColor = EasyColors.SurfaceHigh,
                    disabledContentColor = EasyColors.TextMuted,
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = EasyColors.TextMuted, strokeWidth = 2.5.dp)
                } else {
                    Text("Connect", style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                "Away from home? Make sure Tailscale is switched on.",
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppIcon() {
    Box(
        Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(EasyColors.Accent)
            .semantics { contentDescription = "EasyReader" },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(88.dp))
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = EasyColors.TextMuted) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EasyColors.SurfaceHigh,
            unfocusedContainerColor = EasyColors.SurfaceHigh,
            focusedBorderColor = EasyColors.Accent,
            unfocusedBorderColor = EasyColors.Divider,
            focusedLabelColor = EasyColors.Accent,
            unfocusedLabelColor = EasyColors.TextMuted,
            cursorColor = EasyColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
