package com.example.bitsbeats.data

import android.content.ContentResolver
import android.provider.MediaStore

// Data class para representar un archivo de audio
data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long
)


object MediaRepository {
    // Función para obtener los últimos 10 archivos de audio del sistema (solo música real)
    fun getRecentAudioFiles(contentResolver: ContentResolver): List<AudioFile> {
        val audioFiles = mutableListOf<AudioFile>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        // Filtrar: solo música, duración > 30 segundos, excluir WhatsApp, notificaciones, etc.
        val selection = """
        ${MediaStore.Audio.Media.IS_MUSIC} != 0 
        AND ${MediaStore.Audio.Media.DURATION} > 30000
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%WhatsApp%'
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%Notifications%'
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%Ringtones%'
        AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%Alarms%'
    """.trimIndent()

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            var count = 0
            while (cursor.moveToNext() && count < 10) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Desconocido"
                val artistRaw = cursor.getString(artistColumn)
                val artist = if (artistRaw.isNullOrEmpty() || artistRaw == "<unknown>") "" else artistRaw
                val duration = cursor.getLong(durationColumn)

                audioFiles.add(AudioFile(id, title, artist, duration))
                count++
            }
        }

        return audioFiles
    }

    // Helper para resolver una ruta de archivo a un audioId de MediaStore
    fun queryAudioIdFromPath(contentResolver: ContentResolver, filePath: String): Long? {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                return cursor.getLong(idColumn)
            }
        }
        return null
    }

    // Search MediaStore by title or artist using LIKE (case-insensitive). Returns up to `limit` items.
    fun searchByTitleOrArtist(contentResolver: ContentResolver, query: String, limit: Int = 200): List<AudioFile> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val like = "%${q.replace("%", "\\%").replace("_", "\\_")}%"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "(${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?) AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val selectionArgs = arrayOf(like, like)
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC LIMIT $limit"

        val results = mutableListOf<AudioFile>()
        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Desconocido"
                    val artistRaw = cursor.getString(artistColumn)
                    val artist = if (artistRaw.isNullOrEmpty() || artistRaw == "<unknown>") "" else artistRaw
                    val duration = cursor.getLong(durationColumn)
                    results.add(AudioFile(id, title, artist, duration))
                }
            }
        } catch (_: Exception) {
            // ignore query failures
        }

        return results
    }
}