package com.example.bitsbeats.ui.components

import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.File

// Central playback controller shared across screens (manages MediaPlayer & observable state)
object PlaybackController {
    // Repeat modes
    enum class RepeatMode { OFF, REPEAT_ALL, REPEAT_ONE }
    // current repeat mode
    var repeatMode by mutableStateOf(RepeatMode.OFF)

    // active playlist name (nullable) — set when playing a named playlist
    var activePlaylistName by mutableStateOf<String?>(null)

    // current playing track
    var currentUri by mutableStateOf<String?>(null)
    var title by mutableStateOf("Sin canción")
    var artist by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    // queue management: list of track URIs (strings) and current index
    var queue by mutableStateOf<List<String>>(emptyList())
    var queueIndex by mutableIntStateOf(-1)

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var appContext: Context? = null

    private const val PREFS = "bitsbeats_prefs"
    private const val KEY_LAST_STATE = "last_playback_state"

    /** Play a list of URIs as a queue starting at [startIndex]. This replaces any existing queue. */
    fun playQueue(context: Context, uris: List<String>, startIndex: Int = 0, playlistName: String? = null) {
        if (uris.isEmpty()) return
        // If shuffle is enabled, build a shuffled queue keeping the selected item first
        if (shuffleEnabled) {
            val selected = uris.getOrNull(startIndex.coerceIn(0, uris.size - 1))
            val rest = uris.filter { it != selected }.shuffled()
            queue = if (selected != null) listOf(selected) + rest else uris.shuffled()
            queueIndex = 0
        } else {
            queue = uris
            queueIndex = startIndex.coerceIn(0, uris.size - 1)
        }
        // remember the optional playlist name for UI/navigation
        activePlaylistName = playlistName
        appContext = context.applicationContext
        saveState(context) // persist queue immediately
        playCurrentFromQueue()
    }

    private fun playCurrentFromQueue() {
        val ctx = appContext ?: return
        val idx = queueIndex
        if (idx < 0 || idx >= queue.size) {
            // nothing to play
            stopPlayback()
            return
        }
        val uriStr = queue[idx]
        try {
            val uri = uriStr.toUri()
            playUri(ctx, uri)
        } catch (_: Exception) {
            // skip to next on error
            nextTrack()
        }
    }

    fun playAudioId(context: Context, audioId: Long) {
        try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
            // set single-item queue
            playQueue(context, listOf(uri.toString()), 0)
        } catch (_: Exception) {}
    }

    private fun playUri(context: Context, uri: Uri) {
        try {
            appContext = context.applicationContext
            // query metadata
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        title = c.getString(0) ?: title
                        val rawArtist = c.getString(1)
                        artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "" else rawArtist
                        duration = c.getLong(2)
                    }
                }
                // fallback: if title still blank, try to derive a filename from the uri
                if (title.isBlank() || title == "Sin canción") {
                    try {
                        val path = uri.path
                        if (!path.isNullOrBlank()) title = File(path).name
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            mediaPlayer?.let { mp ->
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mp.setDataSource(pfd.fileDescriptor)
                } ?: throw Exception("No se pudo abrir descriptor para $uri")
                mp.prepare()
                duration = mp.duration.toLong()
                currentUri = uri.toString()
                isPlaying = true
                mp.start()
                // on completion advance to next in queue
                mp.setOnCompletionListener {
                    // handle completion depending on repeat mode
                    when (repeatMode) {
                        RepeatMode.REPEAT_ONE -> {
                            try {
                                mp.seekTo(0)
                                mp.start()
                                isPlaying = true
                                startTicker()
                            } catch (_: Exception) {}
                        }
                        else -> {
                            // REPEAT_ALL and OFF delegate to nextTrack which will handle wrapping for REPEAT_ALL
                            nextTrack()
                        }
                    }
                }
                startTicker()
            }

            saveState(context)
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            isPlaying = false
            stopTicker()
        } else {
            mp.start()
            isPlaying = true
            startTicker()
        }
        // persist play state
        appContext?.let { saveState(it) }
    }

    fun seekTo(ms: Int) {
        try { mediaPlayer?.seekTo(ms); currentPosition = ms.toLong() } catch (_: Exception) {}
        appContext?.let { saveState(it) }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                currentPosition = mediaPlayer?.currentPosition?.toLong() ?: 0L
                // persist occasionally (every 1s)
                saveState(appContext)
                delay(1000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun nextTrack() {
        // If there's no queue, do nothing
        if (queue.isEmpty()) return

        // If there is a next item, advance.
        if (queueIndex + 1 < queue.size) {
            queueIndex += 1
            appContext?.let { saveState(it) }
            playCurrentFromQueue()
            return
        }

        // Reached end of queue
        when (repeatMode) {
            RepeatMode.REPEAT_ALL -> {
                // wrap to start
                if (queue.isNotEmpty()) {
                    queueIndex = 0
                    appContext?.let { saveState(it) }
                    playCurrentFromQueue()
                }
            }
            else -> {
                // OFF: do nothing; REPEAT_ONE handled by onCompletion
                return
            }
        }
    }

    fun prevTrack() {
        // If there's no queue, do nothing
        if (queue.isEmpty()) return

        // If there is a previous item, go back. Otherwise do nothing.
        if (queueIndex - 1 >= 0) {
            queueIndex -= 1
            appContext?.let { saveState(it) }
            playCurrentFromQueue()
        } else {
            // no previous track: do nothing
            return
        }
    }

    private fun stopPlayback() {
        stopTicker()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
        currentUri = null
        currentPosition = 0L
        duration = 0L
        queue = emptyList()
        queueIndex = -1
        // clear persisted state
        appContext?.let { ctx ->
            try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY_LAST_STATE) } } catch (_: Exception) {}
        }
    }

    // Persist playback state (queue, index, position, isPlaying)
    private fun saveState(context: Context?) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("queue", JSONArray(queue))
                put("queueIndex", queueIndex)
                put("position", currentPosition)
                put("isPlaying", isPlaying)
                put("repeatMode", repeatMode.name)
                put("shuffleEnabled", shuffleEnabled)
                put("activePlaylistName", activePlaylistName ?: "")
                put("updatedAt", System.currentTimeMillis())
            }
            prefs.edit { putString(KEY_LAST_STATE, json.toString()) }
        } catch (_: Exception) {}
    }

    // Restore persisted playback state; if queue present, prepare media to that position but do not auto-play unless wasPlaying
    fun restoreState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val s = prefs.getString(KEY_LAST_STATE, null) ?: return
            val json = JSONObject(s)
            val arr = json.optJSONArray("queue") ?: return
            val uris = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val v = arr.optString(i)
                if (v.isNotBlank()) uris.add(v)
            }
            if (uris.isEmpty()) return
            val idx = json.optInt("queueIndex", 0).coerceIn(0, uris.size - 1)
            val pos = json.optLong("position", 0L)
            val repeatStr = json.optString("repeatMode", "OFF")
            repeatMode = try { RepeatMode.valueOf(repeatStr) } catch (_: Exception) { RepeatMode.OFF }
            shuffleEnabled = json.optBoolean("shuffleEnabled", false)
            activePlaylistName = json.optString("activePlaylistName", "").takeIf { it.isNotBlank() }

            // set state
            queue = uris
            queueIndex = idx
            appContext = context.applicationContext

            // prepare media and position, but DO NOT auto-start playback on app open
            try {
                val uri = queue[queueIndex].toUri()
                // Attempt to read metadata (title/artist/duration) so UI shows correct info on restore
                try {
                    context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            title = c.getString(0) ?: title
                            val rawArtist = c.getString(1)
                            artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "" else rawArtist
                            duration = c.getLong(2)
                        }
                    }
                } catch (_: Exception) {}

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer()
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> mediaPlayer?.setDataSource(pfd.fileDescriptor) }
                mediaPlayer?.prepare()
                // prefer duration from mediaPlayer if available
                duration = mediaPlayer?.duration?.toLong() ?: duration
                if (pos > 0) mediaPlayer?.seekTo(pos.toInt())
                currentPosition = pos
                currentUri = queue[queueIndex]
                // keep paused on restore (user must press play)
                isPlaying = false
            } catch (_: Exception) {
                // if prepare fails, clear
                stopPlayback()
            }
        } catch (_: Exception) {}
    }

    fun release() {
        stopTicker()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
    }

    /** Cycle repeat mode: OFF -> REPEAT_ALL -> REPEAT_ONE -> OFF */
    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        appContext?.let { saveState(it) }
    }

    /** Set repeat mode explicitly (renamed to avoid JVM signature clash with generated property setter) */
    fun applyRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        appContext?.let { saveState(it) }
    }

    // shuffle flag
    var shuffleEnabled by mutableStateOf(false)

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        // if enabling shuffle and we have a queue, reshuffle remaining while keeping current
        if (shuffleEnabled && queue.isNotEmpty()) {
            val current = if (queueIndex in queue.indices) queue[queueIndex] else queue.firstOrNull()
            val rest = queue.filter { it != current }.shuffled()
            queue = if (current != null) listOf(current) + rest else rest
            queueIndex = 0
        }
        appContext?.let { saveState(it) }
    }
}
