package com.example.bitsbeats

import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.bitsbeats.ui.components.PlaybackController
import com.example.bitsbeats.ui.components.PlaybackController.PlaybackStateListener
import com.example.bitsbeats.ui.components.PlaybackController.PlaybackStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import android.media.session.MediaSession
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.util.Log

/**
 * Minimal foreground service that exposes playback controls via a notification
 * and a framework MediaSession so the system shows the media controls area.
 */
class MediaPlaybackService : Service() {
    companion object {
        private const val CHANNEL_ID = "bitsbeats_media_channel"
        private const val NOTIFICATION_ID = 0x1001

        const val ACTION_PLAY = "com.example.bitsbeats.action.PLAY"
        const val ACTION_PAUSE = "com.example.bitsbeats.action.PAUSE"
        const val ACTION_NEXT = "com.example.bitsbeats.action.NEXT"
        const val ACTION_PREV = "com.example.bitsbeats.action.PREV"
        const val ACTION_STOP = "com.example.bitsbeats.action.STOP"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var lastSnapshot: PlaybackStateSnapshot? = null
    private var mediaSession: MediaSession? = null
    private var foregroundStarted = false

    private val listener = object : PlaybackStateListener {
        override fun onPlaybackStateChanged(snapshot: PlaybackStateSnapshot) {
            lastSnapshot = snapshot
            // update framework session state + notification
            updateSession(snapshot)
            updateNotification(snapshot)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: MediaPlaybackService starting")
        createNotificationChannel()

        // create framework MediaSession so the system can host media controls area
        try {
            mediaSession = MediaSession(this, "BitsBeatsSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { Log.d(TAG, "MediaSession.onPlay"); PlaybackController.togglePlayPause() }
                    override fun onPause() { Log.d(TAG, "MediaSession.onPause"); PlaybackController.togglePlayPause() }
                    override fun onSkipToNext() { Log.d(TAG, "MediaSession.onSkipToNext"); PlaybackController.nextTrack() }
                    override fun onSkipToPrevious() { Log.d(TAG, "MediaSession.onSkipToPrevious"); PlaybackController.prevTrack() }
                    override fun onSeekTo(pos: Long) { Log.d(TAG, "MediaSession.onSeekTo: $pos"); PlaybackController.seekTo(pos.toInt()) }
                    override fun onStop() { Log.d(TAG, "MediaSession.onStop"); PlaybackController.clearPlaybackAndReset(); stopSelf() }
                })
                // indicate handling of media buttons and transport controls
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                isActive = true
            }
            Log.d(TAG, "MediaSession created and activated")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create MediaSession", e)
            mediaSession = null
        }

        PlaybackController.addStateListener(listener)
    }

    override fun onDestroy() {
        try { PlaybackController.removeStateListener(listener) } catch (_: Exception) {}
        try { mediaSession?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // Ensure we call startForeground quickly when the service is started via startForegroundService
        if (!foregroundStarted) {
            try {
                val placeholder = buildPlaceholderNotification()
                try {
                    startForeground(NOTIFICATION_ID, placeholder)
                    foregroundStarted = true
                    Log.d(TAG, "startForeground called with placeholder notification")
                } catch (e: Exception) {
                    Log.w(TAG, "startForeground placeholder failed", e)
                    // best effort: still proceed
                }
            } catch (e: Exception) {
                Log.w(TAG, "failed to build/start placeholder notification", e)
            }
        }

        if (intent?.action != null) {
            when (intent.action) {
                ACTION_PLAY -> PlaybackController.togglePlayPause()
                ACTION_PAUSE -> PlaybackController.togglePlayPause()
                ACTION_NEXT -> PlaybackController.nextTrack()
                ACTION_PREV -> PlaybackController.prevTrack()
                ACTION_STOP -> {
                    PlaybackController.clearPlaybackAndReset()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun buildPlaceholderNotification(): android.app.Notification {
        val mainIntent = Intent(this@MediaPlaybackService, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(this@MediaPlaybackService, 0, mainIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BitsBeats")
            .setContentText("Reproduciendo")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPending)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("BitsBeats")
                .setContentText("Reproduciendo")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(contentPending)
                .setOngoing(true)
                .build()
        } else {
            builder.build()
        }
    }

    private fun updateSession(snapshot: PlaybackStateSnapshot) {
        try {
            mediaSession?.let { ms ->
                try {
                    val metadataBuilder = MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, snapshot.artist)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, snapshot.duration)

                    ms.setMetadata(metadataBuilder.build())

                    val stateCode = if (snapshot.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                    val actions = (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
                    val pb = PlaybackState.Builder()
                        .setActions(actions)
                        .setState(stateCode, snapshot.currentPosition, 1.0f)
                        .build()
                    ms.setPlaybackState(pb)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(CHANNEL_ID, "Reproducción", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(chan)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(snapshot: PlaybackStateSnapshot) {
        try {
            scope.launch {
                val largeIcon: Bitmap? = try { loadEmbeddedArt(snapshot.currentUri) } catch (_: Exception) { null }

                val mainIntent = Intent(this@MediaPlaybackService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val contentPending = PendingIntent.getActivity(this@MediaPlaybackService, 0, mainIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)

                fun action(action: String, iconRes: Int, title: String): NotificationCompat.Action {
                    val intent = Intent(this@MediaPlaybackService, MediaPlaybackService::class.java).apply { this.action = action }
                    val pi = PendingIntent.getService(this@MediaPlaybackService, action.hashCode(), intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
                    return NotificationCompat.Action.Builder(iconRes, title, pi).build()
                }

                val builder = NotificationCompat.Builder(this@MediaPlaybackService, CHANNEL_ID)
                    .setContentTitle(snapshot.title)
                    .setContentText(if (snapshot.artist.isBlank()) "Artista desconocido" else snapshot.artist)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                    .setOngoing(snapshot.isPlaying)
                    .setContentIntent(contentPending)
                    .setDeleteIntent(PendingIntent.getService(this@MediaPlaybackService, 0, Intent(this@MediaPlaybackService, MediaPlaybackService::class.java).apply { action = ACTION_STOP }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0))

                // actions: prev, play/pause, next
                builder.addAction(action(ACTION_PREV, R.drawable.ic_prev, "Anterior"))
                if (snapshot.isPlaying) builder.addAction(action(ACTION_PAUSE, R.drawable.ic_pause, "Pausar")) else builder.addAction(action(ACTION_PLAY, R.drawable.ic_play, "Reproducir"))
                builder.addAction(action(ACTION_NEXT, R.drawable.ic_next, "Siguiente"))

                largeIcon?.let { builder.setLargeIcon(it) }

                // Build notification using framework builder with MediaStyle when possible so media controls area appears
                val notification = if (mediaSession != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        val nb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            android.app.Notification.Builder(this@MediaPlaybackService, CHANNEL_ID)
                        } else {
                            @Suppress("DEPRECATION")
                            android.app.Notification.Builder(this@MediaPlaybackService)
                        }

                        nb.setContentTitle(snapshot.title)
                            .setContentText(if (snapshot.artist.isBlank()) "Artista desconocido" else snapshot.artist)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setOngoing(snapshot.isPlaying)
                            .setContentIntent(contentPending)

                        // add actions (framework Notification.Builder.addAction(icon, title, pi))
                        nb.addAction(R.drawable.ic_prev, "Anterior", PendingIntent.getService(this@MediaPlaybackService, ACTION_PREV.hashCode(), Intent(this@MediaPlaybackService, MediaPlaybackService::class.java).apply { action = ACTION_PREV }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0))
                        if (snapshot.isPlaying) nb.addAction(R.drawable.ic_pause, "Pausar", PendingIntent.getService(this@MediaPlaybackService, ACTION_PAUSE.hashCode(), Intent(this@MediaPlaybackService, MediaPlaybackService::class.java).apply { action = ACTION_PAUSE }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)) else nb.addAction(R.drawable.ic_play, "Reproducir", PendingIntent.getService(this@MediaPlaybackService, ACTION_PLAY.hashCode(), Intent(this@MediaPlaybackService, MediaPlaybackService::class.java).apply { action = ACTION_PLAY }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0))
                        nb.addAction(R.drawable.ic_next, "Siguiente", PendingIntent.getService(this@MediaPlaybackService, ACTION_NEXT.hashCode(), Intent(this@MediaPlaybackService, MediaPlaybackService::class.java).apply { action = ACTION_NEXT }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0))

                        largeIcon?.let { nb.setLargeIcon(it) }

                        // attach media session token
                        try { nb.setStyle(android.app.Notification.MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0,1,2)) } catch (_: Exception) {}

                        nb.build()
                    } catch (_: Exception) {
                        // fallback to compat builder if framework builder fails
                        builder.build()
                    }
                } else {
                    // compatibility notification
                    builder.build()
                }

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Only post or start foreground if notifications permission is granted (Android 13+)
                val canPost = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) || (ContextCompat.checkSelfPermission(this@MediaPlaybackService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

                // If there is a loaded track (even paused), keep the media notification and foreground so the system can show media controls.
                if (snapshot.currentUri != null) {
                    try {
                        if (canPost) {
                            startForeground(NOTIFICATION_ID, notification)
                        } else {
                            nm.notify(NOTIFICATION_ID, notification)
                        }
                    } catch (_: Exception) {
                        if (canPost) nm.notify(NOTIFICATION_ID, notification)
                    }
                } else {
                    // no track loaded: stop foreground and remove notification
                    try { stopForeground(true) } catch (_: Exception) {}
                    try { nm.cancel(NOTIFICATION_ID) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun startForegroundIfNeeded() {
        // If we have a last snapshot, build and start foreground with it
        lastSnapshot?.let { updateNotification(it) }
    }

    // Load embedded artwork as Bitmap safely
    private fun loadEmbeddedArt(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        var pfd: ParcelFileDescriptor? = null
        try {
            val uri = Uri.parse(uriString)
            pfd = applicationContext.contentResolver.openFileDescriptor(uri, "r")
            val fd: FileDescriptor = pfd?.fileDescriptor ?: return null
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(fd)
                val art = retriever.embeddedPicture ?: return null
                return BitmapFactory.decodeByteArray(art, 0, art.size)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            return null
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
