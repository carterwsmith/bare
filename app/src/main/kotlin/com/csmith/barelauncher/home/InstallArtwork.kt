package com.csmith.barelauncher.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.csmith.barelauncher.R
import com.csmith.barelauncher.install.InstallPhase
import com.csmith.barelauncher.install.InstallTask
import kotlin.math.roundToInt

@Composable
fun installStatusText(task: InstallTask): String = when {
    task.phase.showsProgress && task.fraction != null ->
        stringResource(
            if (task.phase == InstallPhase.Transferring) R.string.status_transferring_percent else R.string.status_downloading_percent,
            (task.fraction * 100).roundToInt(),
        )
    task.error != null -> stringResource(task.error.message)
    else -> stringResource(task.phase.message)
}

/** An install occupies an ordinary app tile until the real package appears. */
@Composable
fun InstallArtwork(task: InstallTask) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.surfaceVariant).padding(12.dp)) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (task.icon != null) {
                    Image(task.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Image(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(24.dp))
                }
                if (task.phase.isBusy && !task.phase.showsProgress) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(
                    installStatusText(task), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.phase == InstallPhase.Failed) colors.error else colors.onSurfaceVariant,
                )
            }
        }
        if (task.phase.showsProgress) {
            Spacer(Modifier.height(8.dp))
            val progress = Modifier.fillMaxWidth().height(4.dp)
            if (task.fraction == null) {
                LinearProgressIndicator(modifier = progress, color = colors.primary, trackColor = colors.onSurface.copy(alpha = 0.15f))
            } else {
                LinearProgressIndicator(progress = { task.fraction }, modifier = progress, color = colors.primary, trackColor = colors.onSurface.copy(alpha = 0.15f))
            }
        }
    }
}
