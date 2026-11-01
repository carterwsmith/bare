package com.csmith.barelauncher.watching

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.net.toUri
import com.csmith.barelauncher.BareLauncherApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val RECORDED_STATES = setOf(
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_PAUSED,
    PlaybackState.STATE_STOPPED,
)

/** Nothing on demand runs for a whole day; anything longer is live TV reporting an endless length. */
private const val MAX_ON_DEMAND_MS = 24 * 60 * 60 * 1000L

/** Where apps put a title's artwork, best first. */
private val ARTWORK_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ART,
    MediaMetadata.METADATA_KEY_ALBUM_ART,
    MediaMetadata.METADATA_KEY_DISPLAY_ICON,
)

/** Where apps put a link to the artwork instead, best first. */
private val ARTWORK_LINK_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ART_URI,
    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
    MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
)

/**
 * Feeds [WatchHistory] from other apps' "now playing" media sessions. Android only shows
 * those sessions to apps with notification access, which is the only reason this is a
 * notification listener: it never reads notifications.
 */
class NowPlayingListener : NotificationListenerService() {
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, NowPlayingListener::class.java) }
    private val app by lazy { application as BareLauncherApp }
    private val artworkLoader by lazy { ArtworkLoader(this, app.http) }
    private val scope = MainScope()
    private var trackers = emptyList<SessionTracker>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        track(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, component)
        track(sessionManager.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged)
        track(emptyList())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun track(controllers: List<MediaController>) {
        trackers.forEach { it.stop() }
        trackers = controllers.map { controller -> SessionTracker(controller).also { it.start() } }
    }

    /**
     * Follows one app's session. Keeps its own copy of the last metadata and state because
     * by the time a callback arrives the controller already reports the new values.
     */
    private inner class SessionTracker(private val controller: MediaController) : MediaController.Callback() {
        private var metadata: MediaMetadata? = null
        private var state: PlaybackState? = controller.playbackState
        private var artwork: Bitmap? = null
        /** Set when the app shared a link to the artwork rather than the image itself. */
        private var artworkLink: Uri? = null
        private var artworkLoad: Job? = null

        fun start() {
            controller.registerCallback(this)
            setMetadata(controller.metadata)
            record()
        }

        /** Saves where playback got to, in case the session is ending. */
        fun stop() {
            record()
            controller.unregisterCallback(this)
            artworkLoad?.cancel()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // Save where the outgoing title got to first. When the next episode autoplays,
            // this is the only sign that the previous one played to the end.
            record()
            setMetadata(metadata)
            record()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Same here: some apps jump straight from playing to stopped at 0:00 when you
            // leave them, so where the outgoing state got to is where you actually were.
            record()
            this.state = state
            record()
        }

        private fun setMetadata(metadata: MediaMetadata?) {
            this.metadata = metadata
            val image = metadata?.let { ARTWORK_KEYS.firstNotNullOfOrNull(it::getBitmap) }
            val link = if (image == null) metadata?.artworkLink() else null
            // Apps resend their metadata often. The same link is already loaded, or on its way.
            if (link != null && link == artworkLink) return
            artworkLoad?.cancel()
            artwork = image
            artworkLink = link
            if (link != null) {
                artworkLoad = scope.launch {
                    artwork = artworkLoader.load(link)
                    record()
                }
            }
        }

        private fun record() {
            val metadata = metadata ?: return
            val state = state ?: return
            if (state.state !in RECORDED_STATES) return
            val title = metadata.watchTitle() ?: return
            // Live TV has no set length, and nothing to come back to.
            val durationMs = metadata.onDemandDurationMs() ?: return

            val entry = WatchEntry(
                packageName = controller.packageName,
                title = title,
                subtitle = (metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: metadata.getText(MediaMetadata.METADATA_KEY_ARTIST))
                    ?.toString()?.takeIf { it.isNotBlank() },
                positionMs = state.currentPosition(),
                durationMs = durationMs,
                lastWatchedAt = System.currentTimeMillis(),
            )
            app.watchHistory.record(entry, artwork)
        }
    }
}

/** The title a watch entry is filed under. */
private fun MediaMetadata.watchTitle(): String? =
    (getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: getText(MediaMetadata.METADATA_KEY_TITLE))
        ?.toString()?.takeIf { it.isNotBlank() }

/**
 * How long the title runs, or null for a live stream. Apps report those as having no
 * length, a negative one, or an endless one.
 */
private fun MediaMetadata.onDemandDurationMs(): Long? =
    getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it in 1..MAX_ON_DEMAND_MS }

private fun MediaMetadata.artworkLink(): Uri? =
    ARTWORK_LINK_KEYS.firstNotNullOfOrNull { key -> getString(key)?.takeIf { it.isNotBlank() }?.toUri() }

/** Apps only report the position when playback changes; work out where it is now. */
private fun PlaybackState.currentPosition(): Long =
    if (state == PlaybackState.STATE_PLAYING) {
        position + ((SystemClock.elapsedRealtime() - lastPositionUpdateTime) * playbackSpeed).toLong()
    } else {
        position
    }
