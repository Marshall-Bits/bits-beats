package com.example.bitsbeats.data

import java.io.File
import android.media.MediaMetadataRetriever

// Data class para representar un elemento del explorador de archivos
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isAudio: Boolean = false,
    val mediaId: Long? = null
)

object FileRepository {
    // Función para obtener el contenido de un directorio (usada por el explorador)
    fun getDirectoryContents(path: String): List<FileItem> {
        val file = File(path)
        val items = mutableListOf<FileItem>()

        if (!file.exists() || !file.isDirectory) {
            return items
        }

        val audioExtensions = listOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "wma")

        file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { child ->
                val extension = child.extension.lowercase()
                val isAudio = audioExtensions.contains(extension)

                if (child.isDirectory || isAudio) {
                    items.add(
                        FileItem(
                            name = child.name,
                            path = child.absolutePath,
                            isDirectory = child.isDirectory,
                            isAudio = isAudio,
                            mediaId = null
                        )
                    )
                }
            }

        return items
    }

    // Search files under provided roots (bounded scan). This reads metadata for audio files to match title/artist.
    // Note: this is potentially slow on large storage; the caller should run this on a background thread.
    fun searchFilesWithMetadata(
        roots: List<String>,
        query: String,
        maxResults: Int = 200,
        maxDepth: Int = 5
    ): List<FileItem> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return emptyList()

        val audioExtensions = listOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "wma")
        val results = mutableListOf<FileItem>()
        val visited = mutableSetOf<String>()

        data class Node(val file: File, val depth: Int)

        val queue = ArrayDeque<Node>()
        roots.forEach { r ->
            try {
                val f = File(r)
                if (f.exists()) queue.add(Node(f, 0))
            } catch (_: Exception) {}
        }

        while (queue.isNotEmpty() && results.size < maxResults) {
            val node = queue.removeFirst()
            val f = node.file
            if (!f.exists()) continue
            val abs = try { f.absolutePath } catch (_: Exception) { continue }
            if (visited.contains(abs)) continue
            visited.add(abs)

            if (f.isDirectory) {
                if (node.depth < maxDepth) {
                    try {
                        f.listFiles()?.forEach { child ->
                            queue.add(Node(child, node.depth + 1))
                        }
                    } catch (_: Exception) {}
                }
            } else {
                val nameLower = f.name.lowercase()
                val ext = f.extension.lowercase()
                val isAudio = audioExtensions.contains(ext)

                var matched = nameLower.contains(normalizedQuery)

                if (isAudio) {
                    // read metadata and check title/artist
                    try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(f.absolutePath)
                        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.lowercase() ?: ""
                        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.lowercase() ?: ""
                        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toLongOrNull() ?: 0L
                        mmr.release()

                        if (title.contains(normalizedQuery) || artist.contains(normalizedQuery)) {
                            matched = true
                        }

                        if (matched) {
                            results.add(
                                FileItem(
                                    name = f.name,
                                    path = f.absolutePath,
                                    isDirectory = false,
                                    isAudio = true,
                                    mediaId = null
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // ignore metadata read failures
                    }
                } else {
                    if (matched) {
                        results.add(
                            FileItem(
                                name = f.name,
                                path = f.absolutePath,
                                isDirectory = false,
                                isAudio = false,
                                mediaId = null
                            )
                        )
                    }
                }
            }
        }

        return results
    }
}