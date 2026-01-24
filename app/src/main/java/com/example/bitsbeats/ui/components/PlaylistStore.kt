package com.example.bitsbeats.ui.components

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

// Playlist storage helpers (simple JSON in SharedPreferences)
object PlaylistStore {
    private const val PREFS = "bitsbeats_prefs"
    private const val KEY_PLAYLISTS = "playlists_json"
    private const val KEY_PLAYLIST_IMAGES = "playlists_images_json"

    // Format: JSON object { "playlistName": [ {"uri": "...", "title":"...","artist":"...","duration":12345}, ... ], ... }
    fun loadAll(context: Context): Map<String, List<Map<String, Any>>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyMap()
        return try {
            val root = JSONObject(s)
            val result = mutableMapOf<String, List<Map<String, Any>>>()
            root.keys().forEach { name ->
                val arr = root.optJSONArray(name) ?: return@forEach
                val list = mutableListOf<Map<String, Any>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val map = mutableMapOf<String, Any>()
                    map["uri"] = obj.optString("uri")
                    map["title"] = obj.optString("title")
                    map["artist"] = obj.optString("artist")
                    map["duration"] = obj.optLong("duration", 0L)
                    list.add(map)
                }
                result[name] = list
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveAll(context: Context, data: Map<String, List<Map<String, Any>>>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = JSONObject()
        data.forEach { (k, v) ->
            val arr = JSONArray()
            v.forEach { item ->
                val obj = JSONObject()
                obj.put("uri", item["uri"] ?: "")
                obj.put("title", item["title"] ?: "")
                obj.put("artist", item["artist"] ?: "")
                obj.put("duration", item["duration"] ?: 0L)
                arr.put(obj)
            }
            root.put(k, arr)
        }
        prefs.edit { putString(KEY_PLAYLISTS, root.toString()) }
    }

    fun createPlaylist(context: Context, name: String): Boolean {
        val all = loadAll(context).toMutableMap()
        if (all.containsKey(name)) return false
        all[name] = emptyList()
        saveAll(context, all)
        return true
    }

    fun deletePlaylist(context: Context, name: String) {
        val all = loadAll(context).toMutableMap()
        all.remove(name)
        saveAll(context, all)
        // remove associated image if present
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val imgJson = prefs.getString(KEY_PLAYLIST_IMAGES, null)
            if (!imgJson.isNullOrBlank()) {
                val root = JSONObject(imgJson)
                if (root.has(name)) {
                    root.remove(name)
                    prefs.edit { putString(KEY_PLAYLIST_IMAGES, root.toString()) }
                }
            }
        } catch (_: Exception) {}
    }

    fun renamePlaylist(context: Context, oldName: String, newName: String): Boolean {
        val all = loadAll(context).toMutableMap()
        if (!all.containsKey(oldName)) return false
        if (all.containsKey(newName)) return false
        val value = all.remove(oldName) ?: emptyList()
        all[newName] = value
        saveAll(context, all)
        // move image entry if present
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val imgJson = prefs.getString(KEY_PLAYLIST_IMAGES, null)
            if (!imgJson.isNullOrBlank()) {
                val root = JSONObject(imgJson)
                val v = root.optString(oldName, "").takeIf { it.isNotBlank() }
                if (!v.isNullOrBlank()) {
                    root.remove(oldName)
                    root.put(newName, v)
                    prefs.edit { putString(KEY_PLAYLIST_IMAGES, root.toString()) }
                }
            }
        } catch (_: Exception) {}
        return true
    }

    fun addItemToPlaylist(context: Context, playlistName: String, uri: String, title: String, artist: String, duration: Long) {
        val all = loadAll(context).toMutableMap()
        val list = all[playlistName]?.toMutableList() ?: mutableListOf()
        val item = mapOf("uri" to uri, "title" to title, "artist" to artist, "duration" to duration)
        list.add(item)
        all[playlistName] = list
        saveAll(context, all)
    }

    fun getPlaylist(context: Context, name: String): List<Map<String, Any>> {
        return loadAll(context)[name] ?: emptyList()
    }

    fun setPlaylistImage(context: Context, name: String, uri: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val root = try { JSONObject(prefs.getString(KEY_PLAYLIST_IMAGES, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
            root.put(name, uri)
            prefs.edit { putString(KEY_PLAYLIST_IMAGES, root.toString()) }
        } catch (_: Exception) {}
    }

    fun getPlaylistImage(context: Context, name: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rootS = prefs.getString(KEY_PLAYLIST_IMAGES, null) ?: return null
            val root = JSONObject(rootS)
            val v = root.optString(name, "")
            v.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }
}
