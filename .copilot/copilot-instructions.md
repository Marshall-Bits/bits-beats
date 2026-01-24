# Documentación interna: arquitectura, convenciones y recursos

Objetivo
- Documentar la arquitectura del proyecto BitsBeats y dejar reglas, convenciones y recomendaciones claras para futuros cambios: dónde poner código, qué patrones seguir, qué recursos/librerías se usan hoy y qué alternativas modernas/probadas se recomiendan.

Resumen rápido (one-liner)
- App Android en Kotlin + Jetpack Compose que reproduce audio del dispositivo, gestiona playlists y navegación; la arquitectura debe separar UI (composables), controladores/estado (PlaybackController / ViewModel) y capa de datos (MediaRepository / FileRepository / PlaylistStore).

Checklist (qué incluye este archivo)
- [x] Mapa de carpetas y paquetes recomendados
- [x] Capas y responsabilidades (UI / Domain / Data / Storage)
- [x] Estado y reproducción (PlaybackController vs ViewModel; recomendaciones)
- [x] Navegación y miniplayer
- [x] Persistencia y formato (playlists, última pista, posición)
- [x] Permisos y MediaStore (Android 13+) y URIs vs audioId
- [x] Librerías actuales detectadas y recomendaciones de reemplazo/versión
- [x] Pruebas, lint, CI y mantenimiento
- [x] Reglas de estilo y naming (breve)

1) Estructura de ficheros y paquetes recomendada
- Raíz: `app/src/main/java/com/example/bitsbeats/`
- UI (pantallas y componentes):
  - `ui.screens` -> pantallas completas (HomeScreen, PlayerScreen, FileBrowserScreen, PlaylistScreens)
  - `ui.components` -> componentes reutilizables (PlaybackMiniPlayer, PlaybackControls, RowItems)
  - `ui.theme` -> Color.kt, Type.kt, Theme.kt
- Capa de dominio/control:
  - `player` o `domain.player` -> `PlaybackController.kt`, interfaces del reproductor, modelos de dominio (Track, QueueItem)
  - `navigation` -> NavGraph / rutas centralizadas
- Capa de datos:
  - `data.media` -> `MediaRepository.kt` (MediaStore queries, mapping a modelos)
  - `data.files` -> `FileRepository.kt` (explorador de filesystem)
  - `data.playlist` -> `PlaylistStore.kt` (persistencia de playlists)
- Utilidades / helpers:
  - `util` -> `TimeUtils.kt`, permisos, Uri helpers
- Tests:
  - `src/test/java/...` y `src/androidTest/java/...`

2) Capas y responsabilidades (separación de preocupaciones)
- UI (Composables): mostrar estado puro y enviar eventos. No realizar consultas de I/O o llamar MediaPlayer directamente.
- Domain / Controller (single source of truth): `PlaybackController` o, preferible, un `PlaybackViewModel` que expone StateFlow/MutableState (estado de la pista actual, cola, posición, isPlaying). Todas las interacciones de reproducción (play, pause, seek, next, prev, setQueue) pasan por aquí.
- Data layer: Repositorios que encapsulan acceso a MediaStore y filesystem y devuelven modelos (por ejemplo: Track(id, uri, title, artist, duration, artworkUri)).
- Persistencia: DataStore (Preferences DataStore) para settings/estado ligero; un fichero JSON en app files o DataStore para playlists; evitar usar SharedPreferences edit lambdas inconsistentes.

3) Estado/side-effects: mejores prácticas
- Hoist state fuera de los composables: ViewModel con StateFlow/LiveData (StateFlow preferible). Composables usan collectAsState() o stateIn.
- Side effects (coroutines, permisos requests, MediaStore queries) en ViewModel/Repository o en rememberCoroutineScope en componentes muy locales; evitar LaunchedEffect con lógica pesada en muchos composables.
- Playback lifecycle: usar un singleton controller que viva junto al process (preferible) o un service (MediaSession/Foreground Service) para reproducción en background y control desde notificaciones. Para una app simple en foreground, un controller central más ViewModel puede bastar.

4) Reproducción y recomendaciones de librería
- Recomendación técnica: migrar a ExoPlayer (o AndroidX Media3) si se quiere estabilidad, formatos y manejo avanzado. MediaPlayer funciona pero es más frágil para listas/colas/background.
- MediaSession + Notification + Foreground Service para playback en background si lo necesitas (sino perderás control al sacar la app del foreground).
- Control de URIs vs audioId: almacenar URIs (content://) es más robusto y persistente que IDs (que pueden cambiar si el archivo se mueve o se reindexa). Guardar además metadata (title, artist, duration) para mostrar UI aun si el proveedor no responde.

5) Persistencia de estado (cómo guardar última pista + posición + playlist)
- Usar Preferences DataStore para estado pequeño (ultimaUri, positionMillis, isPlaying, activePlaylistName, activeQueueJson)
- Guardar la `queue` como JSON (array de objetos {uri,title,artist,duration}) en DataStore o en un fichero en app files. Evitar SharedPreferences por migración.
- On app start: restaurar estado (sin auto-play). Mostrar mini-player en pausa con la información restaurada.

6) Permisos y MediaStore (Android 13+)
- Android 13 (API 33) introdujo permisos granulares: `READ_MEDIA_AUDIO`.
- Compatibilidad: en tiempo de ejecución pedir `READ_MEDIA_AUDIO` en API>=33, en API <33 pedir `READ_EXTERNAL_STORAGE`.
- Usar `ActivityResultContracts.RequestPermission()` para peticiones simples.
- Evitar acceso directo a file paths en Android 11+ (scoped storage). Preferir MediaStore queries y content URIs.

7) Librerías actuales detectadas (del código) y qué significan
- Jetpack Compose (androidx.compose.*) — UI declarativa.
- Material3 (androidx.compose.material3) — componentes Material 3.
- Navigation Compose (androidx.navigation.compose) — navegación entre composables.
- Kotlin Coroutines (kotlinx.coroutines) — concurrencia asincrona.
- AndroidX Activity (activity-compose) — integración de Compose con Activity.
- MediaStore (android.provider.MediaStore) — acceso a la biblioteca de medios del dispositivo.
- SharedPreferences (actualmente usado para playlists en JSON) — considerar migrar a DataStore.
- androidx.core (core-ktx) — utilidades Kotlin.

Librerías recomendadas/alternativas modernas
- Reproductor: ExoPlayer / Media3 (mejor que MediaPlayer): `com.google.android.exoplayer:exoplayer` o `androidx.media3:media3-exoplayer`.
- Persistencia de preferencias: `androidx.datastore:datastore-preferences` (Kotlin coroutines + Flow friendly).
- DI (opcional pero recomendado): Hilt (`com.google.dagger:hilt-android`) para inyectar repositorios y controller.
- Lifecycle / ViewModel: `androidx.lifecycle:lifecycle-viewmodel-ktx` y `lifecycle-runtime-compose`.
- Compose BOM: usar `androidx:compose-bom` para mantener versiones consistentes.

APIs a evitar / obsoletas / notas
- Evitar usar SharedPreferences para estados complejos; preferir DataStore.
- Evitar usar file-system absolute paths y manipular `Environment.getExternalStorageDirectory()` (scoped storage). Usa Storage Access Framework o MediaStore + SAF.
- Evitar dependencias antiguas de soporte libs; usar AndroidX actualizadas.

8) Testing y QA
- Unit tests:
  - Testear lógica de `PlaybackController` y `PlaylistStore` (mockear repositorios y SharedPreferences/DataStore).
- Instrumented tests:
  - Pequeñas pruebas de UI con Compose Testing (compose-test) para pantallas clave.
- Lint / static checks: habilitar Ktlint / detekt y Android Lint en CI.

9) CI / Repositorio / calidad
- Recomendado: pipeline CI (GitHub Actions / GitLab CI) con pasos:
  - Gradle build (assembleDebug) y unit tests
  - Android lint
  - Opcional: run compose UI tests on emulators (más lento)
- Pre-commit hooks: ktlint autoformat.

10) Reglas de estilo y convenciones (breve)
- Nombre de ficheros: `XxxScreen.kt`, `XxxViewModel.kt`, `PlaybackController.kt`, `MediaRepository.kt`.
- Cada archivo contiene una responsabilidad única (single responsibility). Mantener composables pequeños (<200 LOC si es posible).
- Composables: recibir estado puro y callbacks (ej.: PlayerScreen(state: PlayerState, onPlay: ()->Unit)). Evitar que los composables hagan I/O o corrutinas largas.
- Eventos UI → ViewModel/Controller: UI solo envía intents/acciones.
- State exposure: usar data classes inmutables para estado y exponer StateFlow o Compose State.

11) Mapeo de recursos actuales (qué se está usando hoy en el código)
- UI: Jetpack Compose + Material3
- Navigation: Navigation Compose
- Coroutines: kotlinx.coroutines
- Media access: MediaStore queries (getRecentAudioFiles, queryAudioIdFromPath)
- Playback: `PlaybackController` (implementación propia, usa MediaPlayer internamente actualmente)
- Playlists: JSON en SharedPreferences (PlaylistStore)

12) Migraciones y tareas prioritarias (roadmap)
- Corto plazo (prioridad alta):
  - Mover todas las composables a paquetes `ui.screens` / `ui.components` (ya parcialmente hecho).
  - Reemplazar SharedPreferences por DataStore para persistencia de estado/playlists.
  - Consolidar un único `PlaybackController` o mejor, `PlaybackViewModel` y definir su API público (playUri(uri), playQueue(list, index), togglePlayPause(), seekTo(ms), next(), prev(), getState()).
- Medio plazo:
  - Migrar playback a ExoPlayer/Media3 (mejor soporte de cola, notificaciones y background).
  - Implementar MediaSession + Foreground Service si se requiere playback en background.
- Largo plazo:
  - Añadir DI (Hilt), tests unitarios e instrumentados en CI.

13) Formatos de persistencia (recomendado)
- Estado del reproductor (DataStore - Preferences):
  - last_played_uri: String
  - last_position_ms: Long
  - last_is_playing: Boolean (pero preferir false on restore)
  - active_playlist_name: String
- Playlists: JSON persistido en fichero `filesDir/playlists.json` o DataStore (mapa de String->Array).

14) Reglas concretas para nuevos commits / PRs
- Cada PR pequeño y con un objetivo (mover un screen, arreglar un bug, añadir prueba).
- Añadir tests cuando se cambia lógica del dominio (playlist, playback que afectan UX).
- Seguir el package layout y naming aquí descrito.

15) Snippets y patrones recomendados (rápido)
- ViewModel-state exposure (esquema):

```kotlin
// in PlaybackViewModel.kt
private val _uiState = MutableStateFlow(PlayerUiState())
val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

fun playUri(uri: String) {
  viewModelScope.launch { playbackController.playUri(uri); updateState() }
}
```

- Composable receives state

```kotlin
@Composable
fun PlayerScreen(state: PlayerUiState, onPlay: ()->Unit, onSeek: (Int)->Unit) {
  // purely rendering state, no repository calls
}
```

16) Recursos / enlaces útiles (de referencia rápida)
- ExoPlayer / Media3 docs: https://exoplayer.dev/  and https://developer.android.com/media
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Compose navigation: https://developer.android.com/jetpack/compose/navigation
- Permissions (Android 13): https://developer.android.com/about/versions/13/behavior-changes-13

17) Notas finales para el agente (tus futuras tareas)
- Antes de mover o importar nuevas librerías: comprobar `build.gradle(.kts)` y usar versiones coherentes (Compose BOM). Evitar añadir libs que el proyecto no tenga y que requieran cambios de configuración masiva sin avisar.
- Cuando añadas nuevos archivos, ubícalos según la jerarquía propuesta para mantener consistencia.
- Si introduces reproducción en background, crea una interfaz `Player` que abstraiga `ExoPlayer` o `MediaPlayer` para poder mockear en tests.

---

Archivo actualizado: inclúyelo en el repo y úsalo como referencia viva; actualiza cuando migréis a ExoPlayer / DataStore o añadáis DI.

