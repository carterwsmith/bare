package com.csmith.barelauncher.install

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.csmith.barelauncher.R
import kotlin.math.roundToInt

@Composable
fun InstallScreen(state: InstallState, onInstall: (String) -> Unit) {
    var link by rememberSaveable { mutableStateOf("https://") }
    val linkFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { linkFocus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(R.string.install_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.install_hint), style = MaterialTheme.typography.bodyLarge)
        LinkField(
            value = link,
            onValueChange = { link = it },
            onGo = { onInstall(link) },
            modifier = Modifier.focusRequester(linkFocus),
        )
        Button(onClick = { onInstall(link) }) { Text(stringResource(R.string.install_button)) }
        InstallStatus(state)
    }
}

@Composable
private fun LinkField(
    value: String,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) colors.primary else colors.border, RoundedCornerShape(8.dp))
            .padding(16.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        // Close the keyboard so the download status underneath is visible.
        keyboardActions = KeyboardActions(onGo = {
            keyboard?.hide()
            onGo()
        }),
    )
}

@Composable
private fun InstallStatus(state: InstallState) {
    val text = when (state) {
        InstallState.Idle -> return
        is InstallState.Downloading -> when (val fraction = state.fraction) {
            null -> stringResource(R.string.status_downloading)
            else -> stringResource(R.string.status_downloading_percent, (fraction * 100).roundToInt())
        }
        InstallState.Confirming -> stringResource(R.string.status_confirming)
        InstallState.Installed -> stringResource(R.string.status_installed)
        is InstallState.Failed -> listOfNotNull(stringResource(state.error.message), state.detail)
            .joinToString("\n")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (state is InstallState.Failed) MaterialTheme.colorScheme.error else Color.Unspecified,
    )
}
