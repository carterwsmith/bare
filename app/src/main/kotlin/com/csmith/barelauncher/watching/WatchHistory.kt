package com.csmith.barelauncher.watching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val MAX_ENTRIES = 10

/**
 * Anything shorter is a trailer, an ad or an autoplay preview. Those can carry a show's
 * title, so they never count: not as watching it, and not as finishing it.
 */
private const val MIN_WATCHED_MS = 60_000L

/** Past this point the credits are rolling, so it counts as finished. */
private const val FINISHED_PROGRESS = 0.95f

/** Apps report their playback position often, so writes to disk are batched. */
private const val SAVE_DELAY_MS = 2_000L

private const val ARTWORK_WIDTH = 512

/** What's been watched across apps, newest first. Kept on disk so it survives restarts. */
class WatchHistory(filesDir: File, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(filesDir, "watch-history.json"))
    private val artworkDir = File(filesDir, "artwork")
    // One writer at a time, so artwork and history writes never race each other.
    private val disk = Dispatchers.IO.limitedParallelism(1)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<WatchEntry>> = _entries.asStateFlow()

    init {
        scope.launch(disk) {
            _entries.drop(1).collectLatest { entries ->
                delay(SAVE_DELAY_MS)
                save(entries)
            }
        }
    }

    fun record(entry: WatchEntry, artwork: Bitmap?) {
        if (entry.positionMs < MIN_WATCHED_MS) return
        if (entry.progress?.let { it >= FINISHED_PROGRESS } == true) {
            _entries.update { current -> current.filterNot { it.id == entry.id } }
            return
        }
        _entries.update { current ->
            (listOf(entry) + current.filterNot { it.id == entry.id }).take(MAX_ENTRIES)
        }
        if (artwork != null) {
            scope.launch(disk) {
                if (!artworkFile(entry.id).exists()) saveArtwork(entry.id, artwork)
            }
        }
    }

    /** Null when the app never shared artwork for this entry. */
    fun loadArtwork(id: String): Bitmap? = BitmapFactory.decodeFile(artworkFile(id).path)

    private fun artworkFile(id: String) = File(artworkDir, "$id.jpg")

    private fun load(): List<WatchEntry> =
        if (file.baseFile.exists()) {
            file.openRead().use { Json.decodeFromString(it.readBytes().decodeToString()) }
        } else {
            emptyList()
        }

    private fun save(entries: List<WatchEntry>) {
        file.write { it.write(Json.encodeToString(entries).toByteArray()); true }
        // Also clears out artwork of dropped entries and any half-written files.
        val kept = entries.map { artworkFile(it.id) }.toSet()
        artworkDir.listFiles()?.filterNot { it in kept }?.forEach { it.delete() }
    }

    private fun saveArtwork(id: String, artwork: Bitmap) {
        val scaled = if (artwork.width > ARTWORK_WIDTH) {
            artwork.scale(ARTWORK_WIDTH, artwork.height * ARTWORK_WIDTH / artwork.width)
        } else {
            artwork
        }
        artworkDir.mkdirs()
        AtomicFile(artworkFile(id)).write { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}

/**
 * Writes to a temporary file that replaces the real one only once [block] succeeds, so a
 * process killed mid-write never leaves a broken file behind.
 */
private inline fun AtomicFile.write(block: (FileOutputStream) -> Boolean) {
    val out = startWrite()
    try {
        if (block(out)) finishWrite(out) else failWrite(out)
    } catch (e: IOException) {
        failWrite(out)
        throw e
    }
}
