package com.csmith.barelauncher.watching

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.csmith.barelauncher.BareLauncherApp

private val RECORDED_STATES = setOf(
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_PAUSED,
    PlaybackState.STATE_STOPPED,
)

/**
 * Feeds [WatchHistory] from other apps' "now playing" media sessions. Android only shows
 * those sessions to apps with notification access, which is the only reason this is a
 * notification listener: it never reads notifications.
 */
class NowPlayingListener : NotificationListenerService() {
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, NowPlayingListener::class.java) }
    private val history by lazy { (application as BareLauncherApp).watchHistory }
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

    private fun track(controllers: List<MediaController>) {
        trackers.forEach { it.stop() }
        trackers = controllers.map { controller -> SessionTracker(controller).also { it.start() } }
    }

    /**
     * Follows one app's session. Keeps its own copy of the last metadata and state because
     * by the time a callback arrives the controller already reports the new values.
     */
    private inner class SessionTracker(private val controller: MediaController) : MediaController.Callback() {
        private var metadata: MediaMetadata? = controller.metadata
        private var state: PlaybackState? = controller.playbackState

        fun start() {
            controller.registerCallback(this)
            record()
        }

        /** Saves where playback got to, in case the session is ending. */
        fun stop() {
            record()
            controller.unregisterCallback(this)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // Save where the outgoing title got to first. When the next episode autoplays,
            // this is the only sign that the previous one played to the end.
            record()
            this.metadata = metadata
            record()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Same here: some apps jump straight from playing to stopped at 0:00 when you
            // leave them, so where the outgoing state got to is where you actually were.
            record()
            this.state = state
            record()
        }

        private fun record() {
            val metadata = metadata ?: return
            val state = state ?: return
            if (state.state !in RECORDED_STATES) return
            val title = metadata.watchTitle() ?: return

            val entry = WatchEntry(
                packageName = controller.packageName,
                title = title,
                subtitle = (metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: metadata.getText(MediaMetadata.METADATA_KEY_ARTIST))
                    ?.toString()?.takeIf { it.isNotBlank() },
                positionMs = state.currentPosition(),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
                lastWatchedAt = System.currentTimeMillis(),
            )
            val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            history.record(entry, artwork)
        }
    }
}

/** The title a watch entry is filed under. */
private fun MediaMetadata.watchTitle(): String? =
    (getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: getText(MediaMetadata.METADATA_KEY_TITLE))
        ?.toString()?.takeIf { it.isNotBlank() }

/** Apps only report the position when playback changes; work out where it is now. */
private fun PlaybackState.currentPosition(): Long =
    if (state == PlaybackState.STATE_PLAYING) {
        position + ((SystemClock.elapsedRealtime() - lastPositionUpdateTime) * playbackSpeed).toLong()
    } else {
        position
    }
