package com.example.bitsbeats.ui.components

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Simple JSON-backed stats store saved in SharedPreferences under a single key.
 * Stores per-song counts and last-played timestamps, per-playlist counts and last-played timestamps,
 * and per-artist counts (artists with empty name are NOT recorded).
 */
object StatsStore {
    private const val PREFS = "bitsbeats_prefs"
    private const val KEY_STATS = "stats_json"

    private fun load(context: Context): JSONObject {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val s = prefs.getString(KEY_STATS, null) ?: return JSONObject()
            try { JSONObject(s) } catch (_: Exception) { JSONObject() }
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun save(context: Context, json: JSONObject) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit { putString(KEY_STATS, json.toString()) }
        } catch (_: Exception) {}
    }

    fun recordSongPlay(context: Context, uri: String, title: String?, artist: String?) {
        try {
            val now = System.currentTimeMillis()
            val root = load(context)
            val songs = root.optJSONObject("songs") ?: JSONObject().also { root.put("songs", it) }
            val entry = songs.optJSONObject(uri) ?: JSONObject()
            val cnt = entry.optInt("count", 0) + 1
            entry.put("count", cnt)
            entry.put("lastPlayed", now)
            if (!title.isNullOrBlank()) entry.put("title", title)
            // also store artist on the song entry when available so UI can display it
            if (!artist.isNullOrBlank()) entry.put("artist", artist)
            songs.put(uri, entry)

            // global quick access
            root.put("lastSongUri", uri)
            root.put("lastSongTs", now)

            // record artist count only when non-blank
            if (!artist.isNullOrBlank()) {
                val artists = root.optJSONObject("artists") ?: JSONObject().also { root.put("artists", it) }
                val aCnt = artists.optInt(artist, 0) + 1
                artists.put(artist, aCnt)
            }

            save(context, root)
        } catch (_: Exception) {}
    }

    fun recordPlaylistPlay(context: Context, playlistName: String) {
        try {
            val now = System.currentTimeMillis()
            val root = load(context)
            val pls = root.optJSONObject("playlists") ?: JSONObject().also { root.put("playlists", it) }
            val entry = pls.optJSONObject(playlistName) ?: JSONObject()
            val cnt = entry.optInt("count", 0) + 1
            entry.put("count", cnt)
            entry.put("lastPlayed", now)
            pls.put(playlistName, entry)

            // global quick access
            root.put("lastPlaylistName", playlistName)
            root.put("lastPlaylistTs", now)

            save(context, root)
        } catch (_: Exception) {}
    }

    fun getSongPlayCount(context: Context, uri: String): Int {
        return try {
            val root = load(context)
            val songs = root.optJSONObject("songs") ?: return 0
            songs.optJSONObject(uri)?.optInt("count", 0) ?: 0
        } catch (_: Exception) { 0 }
    }

    fun getPlaylistPlayCount(context: Context, playlistName: String): Int {
        return try {
            val root = load(context)
            val pls = root.optJSONObject("playlists") ?: return 0
            pls.optJSONObject(playlistName)?.optInt("count", 0) ?: 0
        } catch (_: Exception) { 0 }
    }

    fun getArtistPlayCount(context: Context, artist: String): Int {
        if (artist.isBlank()) return 0
        return try {
            val root = load(context)
            val artists = root.optJSONObject("artists") ?: return 0
            artists.optInt(artist, 0)
        } catch (_: Exception) { 0 }
    }

    fun getLastSongPlayedTimestamp(context: Context, uri: String): Long? {
        return try {
            val root = load(context)
            val songs = root.optJSONObject("songs") ?: return null
            val v = songs.optJSONObject(uri)?.optLong("lastPlayed", 0L) ?: 0L
            if (v <= 0L) null else v
        } catch (_: Exception) { null }
    }

    fun getLastPlaylistPlayedTimestamp(context: Context, playlistName: String): Long? {
        return try {
            val root = load(context)
            val pls = root.optJSONObject("playlists") ?: return null
            val v = pls.optJSONObject(playlistName)?.optLong("lastPlayed", 0L) ?: 0L
            if (v <= 0L) null else v
        } catch (_: Exception) { null }
    }

    fun exportStats(context: Context): JSONObject {
        return try { load(context) } catch (_: Exception) { JSONObject() }
    }

    // helper: top N songs by count (uri -> count)
    fun topSongs(context: Context, limit: Int = 10): List<Pair<String, Int>> {
        return try {
            val root = load(context)
            val songs = root.optJSONObject("songs") ?: return emptyList()
            val list = mutableListOf<Pair<String, Int>>()
            val keys = songs.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val count = songs.optJSONObject(k)?.optInt("count", 0) ?: 0
                list.add(k to count)
            }
            list.sortedByDescending { it.second }.take(limit)
        } catch (_: Exception) { emptyList() }
    }
}
