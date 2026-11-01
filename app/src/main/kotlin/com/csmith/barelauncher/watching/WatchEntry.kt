package com.csmith.barelauncher.watching

import kotlinx.serialization.Serializable
import java.util.UUID

/** Something watched in another app, as it was when last seen playing. */
@Serializable
data class WatchEntry(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val positionMs: Long,
    /** Null when the app doesn't report a length, e.g. for live streams. */
    val durationMs: Long?,
    val lastWatchedAt: Long,
) {
    /** The same title in the same app always gets the same id, however often it's updated. */
    val id: String
        get() = UUID.nameUUIDFromBytes("$packageName\n$title".toByteArray()).toString()

    val progress: Float?
        get() = durationMs?.let { positionMs.toFloat() / it }
}
