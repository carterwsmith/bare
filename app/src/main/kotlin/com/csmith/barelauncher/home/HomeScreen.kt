package com.csmith.barelauncher.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.csmith.barelauncher.R

private const val COLUMNS = 5

/** Keys that put a moving app down. */
private val MOVE_DONE_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.Back, Key.Menu)

@Composable
fun HomeScreen(
    apps: List<LaunchableApp>,
    pinned: List<String>,
    hidden: List<LaunchableApp>,
    keepWatching: List<KeepWatchingItem>,
    onLaunch: (LaunchableApp) -> Unit,
    onMove: (LaunchableApp, offset: Int) -> Unit,
    onTogglePin: (LaunchableApp) -> Unit,
    onHide: (LaunchableApp) -> Unit,
    onShow: (LaunchableApp) -> Unit,
    onInstallApk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val firstApp = remember { FocusRequester() }
    var firstAppFocusRequests by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var showingHidden by remember { mutableStateOf(false) }
    var movingPackage by remember { mutableStateOf<String?>(null) }

    // The cursor starts on the first app whenever the home screen comes back to the front,
    // and returns there whenever the tile it was on goes away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        movingPackage = null
        firstAppFocusRequests++
    }
    LaunchedEffect(firstAppFocusRequests, apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        gridState.scrollToItem(0)
        // Wait one frame so the grid has laid out its first row.
        withFrameNanos {}
        firstApp.requestFocus()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(
                app = app,
                index = index,
                pinned = app.packageName in pinned,
                moving = app.packageName == movingPackage,
                onClick = { onLaunch(app) },
                onMenu = { menuFor = app },
                onMoveBy = { offset -> onMove(app, offset) },
                onMoveDone = { movingPackage = null },
                modifier = if (index == 0) Modifier.focusRequester(firstApp) else Modifier,
            )
        }
        if (keepWatching.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.keep_watching),
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // A single row, lined up with the apps above it.
            items(keepWatching.take(COLUMNS), key = { it.entry.id }) { item ->
                // Opening the app brings back its existing screen, so an app still holding a
                // paused episode returns to it.
                WatchCard(item = item, onClick = { onLaunch(item.app) })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Set apart from the cards so the buttons don't read as another row of them.
            Row(
                modifier = Modifier.padding(top = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(onClick = onInstallApk) { Text(stringResource(R.string.install_apk)) }
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                if (hidden.isNotEmpty()) {
                    Button(onClick = { showingHidden = true }) {
                        Text(stringResource(R.string.hidden_apps, hidden.size))
                    }
                }
            }
        }
    }

    menuFor?.let { app ->
        AppMenuDialog(
            app = app,
            pinned = app.packageName in pinned,
            onMove = {
                movingPackage = app.packageName
                menuFor = null
            },
            onTogglePin = {
                onTogglePin(app)
                menuFor = null
            },
            onHide = {
                onHide(app)
                menuFor = null
                firstAppFocusRequests++
            },
            onDismiss = { menuFor = null },
        )
    }
    if (showingHidden) {
        HiddenAppsDialog(
            apps = hidden,
            onShow = { app ->
                onShow(app)
                if (hidden.size == 1) {
                    // That was the last one, and the Hidden apps button goes with it.
                    showingHidden = false
                    firstAppFocusRequests++
                }
            },
            onDismiss = { showingHidden = false },
        )
    }
}

@Composable
private fun AppCard(
    app: LaunchableApp,
    index: Int,
    pinned: Boolean,
    moving: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onMoveBy: (offset: Int) -> Unit,
    onMoveDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    if (moving) {
        // Scroll along with the tile as it's moved past the edge of the screen.
        LaunchedEffect(index) {
            withFrameNanos {}
            bringIntoView.bringIntoView()
        }
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .aspectRatio(16f / 9f)
            .onPreviewKeyEvent { event ->
                when {
                    moving -> handleMoveKey(event, onMoveBy, onMoveDone)
                    event.key == Key.Menu -> {
                        // Act on release: holding Menu belongs to Home on Fire, which swallows the release.
                        if (event.type == KeyEventType.KeyUp) onMenu()
                        true
                    }
                    else -> false
                }
            },
        border = if (moving) {
            CardDefaults.border(focusedBorder = Border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary)))
        } else {
            CardDefaults.border()
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppArtwork(app)
            if (pinned) PinBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (moving) MoveArrows()
        }
    }
}

/** Arrows on all four sides of the moving tile, showing it can be moved with the remote. */
@Composable
private fun MoveArrows() {
    val chevron = painterResource(R.drawable.ic_chevron)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        for ((alignment, degrees) in MOVE_ARROWS) {
            Image(
                painter = chevron,
                contentDescription = null,
                modifier = Modifier.align(alignment).size(28.dp).rotate(degrees),
            )
        }
    }
}

/** Where each arrow sits on the tile, and how far to turn the right-pointing chevron. */
private val MOVE_ARROWS = listOf(
    Alignment.CenterEnd to 0f,
    Alignment.BottomCenter to 90f,
    Alignment.CenterStart to 180f,
    Alignment.TopCenter to 270f,
)

/** While an app is moving, the arrows move it and OK, Back or Menu put it down. */
private fun handleMoveKey(event: KeyEvent, onMoveBy: (Int) -> Unit, onMoveDone: () -> Unit): Boolean {
    val offset = when (event.key) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        Key.DirectionUp -> -COLUMNS
        Key.DirectionDown -> COLUMNS
        else -> null
    }
    if (offset != null) {
        if (event.type == KeyEventType.KeyDown) onMoveBy(offset)
        return true
    }
    if (event.key in MOVE_DONE_KEYS) {
        if (event.type == KeyEventType.KeyUp) onMoveDone()
        return true
    }
    return false
}

@Composable
private fun PinBadge(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_pin),
        contentDescription = stringResource(R.string.pinned),
        modifier = modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(4.dp),
    )
}

/** The app's TV banner, or its icon and name when it has no banner. */
@Composable
fun AppArtwork(app: LaunchableApp) {
    val banner = app.banner
    if (banner != null) {
        Image(
            bitmap = banner,
            contentDescription = app.label,
            // Not every banner is exactly 16:9; never crop off part of an app's name.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(
                text = app.label,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
