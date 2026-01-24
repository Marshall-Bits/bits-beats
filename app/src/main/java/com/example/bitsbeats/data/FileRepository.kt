package com.example.bitsbeats.data

import java.io.File

// Data class para representar un elemento del explorador de archivos
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isAudio: Boolean = false
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
                            isAudio = isAudio
                        )
                    )
                }
            }

        return items
    }
}