package com.example.bitsbeats.util

import android.annotation.SuppressLint

// Función para formatear la duración en mm:ss
@SuppressLint("DefaultLocale")
fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}
