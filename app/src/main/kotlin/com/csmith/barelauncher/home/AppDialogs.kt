package com.csmith.barelauncher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.csmith.barelauncher.R

/** What the remote's Menu button offers for an app on the home screen. */
@Composable
fun AppMenuDialog(
    app: LaunchableApp,
    pinned: Boolean,
    onMove: () -> Unit,
    onTogglePin: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    ButtonDialog(title = app.label, onDismiss = onDismiss) { firstButton ->
        Button(onClick = onMove, modifier = firstButton.fillMaxWidth()) {
            Text(stringResource(R.string.move_app))
        }
        Button(onClick = onTogglePin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (pinned) R.string.unpin else R.string.pin_to_top))
        }
        Button(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hide_app))
        }
    }
}

@Composable
fun HiddenAppsDialog(apps: List<LaunchableApp>, onShow: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    ButtonDialog(title = stringResource(R.string.hidden_apps_title), onDismiss = onDismiss) { firstButton ->
        Text(stringResource(R.string.hidden_apps_hint), style = MaterialTheme.typography.bodyMedium)
        apps.forEachIndexed { index, app ->
            Button(
                onClick = { onShow(app) },
                modifier = (if (index == 0) firstButton else Modifier).fillMaxWidth(),
            ) {
                Text(app.label)
            }
        }
    }
}

/** A titled column of buttons. Focus starts on the one given [content]'s modifier. */
@Composable
private fun ButtonDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(firstButton: Modifier) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val firstButton = remember { FocusRequester() }
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.width(440.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content(Modifier.focusRequester(firstButton))
            }
        }
        LaunchedEffect(Unit) { firstButton.requestFocus() }
    }
}
