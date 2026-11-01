package com.csmith.barelauncher.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.csmith.barelauncher.R
import com.csmith.barelauncher.watching.WatchEntry

data class KeepWatchingItem(
    val entry: WatchEntry,
    val app: LaunchableApp,
    /** Null when the app didn't share any; the card shows the app's own artwork instead. */
    val artwork: ImageBitmap?,
)

@Composable
fun KeepWatchingRow(items: List<KeepWatchingItem>, onOpen: (KeepWatchingItem) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.keep_watching),
            modifier = Modifier.padding(horizontal = 48.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(items, key = { it.entry.id }) { item ->
                WatchCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun WatchCard(item: KeepWatchingItem, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp)) {
        Card(onClick = onClick, modifier = Modifier.aspectRatio(16f / 9f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                val artwork = item.artwork
                if (artwork != null) {
                    Image(
                        bitmap = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AppArtwork(item.app)
                }
                item.entry.progress?.let { progress ->
                    ProgressBar(progress, Modifier.align(Alignment.BottomStart))
                }
            }
        }
        Text(
            text = item.entry.title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(item.entry.subtitle, item.app.label).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
