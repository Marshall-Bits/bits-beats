
Resumen rápido (one-liner)
- App Android en Kotlin + Jetpack Compose que reproduce audio del dispositivo, gestiona playlists y navegación; la arquitectura debe separar UI (composables), controladores/estado (PlaybackController / ViewModel) y capa de datos (MediaRepository / FileRepository / PlaylistStore).
 
Reglas operativas
- Nunca eliminar imports: no borrar automáticamente líneas de import durante refactors o limpiezas; si un import parece no usado, se revisará manualmente.
- Ejecutar siempre una verificación de errores `get_errors` y confirmar que no quedan errores de compilación o importación antes de cerrar la tarea o hacer merge.
- No usar `Gradle build` nunca.

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

Nota importante (playlist activa)
- Cuando la reproducción proviene de una "playlist" (es decir, el usuario pulsa PLAY en una playlist), el sistema debe almacenar el nombre de la playlist activa en el estado del reproductor. Esto permite que la UI (mini-player, PlayerScreen y la navegación inferior) sepan qué playlist está en curso y puedan navegar directamente a sus detalles.

Recomendación de implementación rápida (Kotlin)
- En el controlador de reproducción (ej. `PlaybackController`) añadir un campo observable:

```kotlin
// en PlaybackController
var activePlaylistName by mutableStateOf<String?>(null)
```

- Exponer un parámetro opcional en la función que inicia la reproducción de una lista para registrar el nombre de la playlist:

```kotlin
fun playQueue(context: Context, uris: List<String>, startIndex: Int = 0, playlistName: String? = null) {
    if (uris.isEmpty()) return
    // ... construir cola / shuffle ...
    activePlaylistName = playlistName
    // persistir estado && reproducir
}
```

- Persistir `activePlaylistName` junto con las demás propiedades en `saveState()` y restaurarla en `restoreState()` (JSON pref de `last_playback_state`). Esto asegura que al reabrir la app el nombre de la playlist activa (y por tanto la navegación) se mantiene.

Ejemplo de JSON (clave `last_playback_state` en SharedPreferences o DataStore):

```json
{
  "queue": ["content://...", ...],
  "queueIndex": 0,
  "position": 12345,
  "isPlaying": false,
  "repeatMode": "OFF",
  "shuffleEnabled": false,
  "activePlaylistName": "Mis favoritos",
  "updatedAt": 167...
}
```

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

8) Imágenes default
- Canción: `res.drawable.song_default`` (imagen genérica para pistas sin artwork).`
- Playlist: `res.drawable.playlist_default` (imagen genérica para playlists sin imagen asociada).

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

Comportamiento del botón Playlists en la navegación inferior
- Especificación: el icono/entrada "Playlists" del bottom navigation debe comportarse así:
  - Si existe una playlist activa (`PlaybackController.activePlaylistName` no es null/blank): navegar directamente a `PlaylistDetailScreen` de esa playlist (ruta `playlistDetail/{name}` codificada con URLEncoder).
  - En caso contrario: navegar a la pantalla de listado de playlists (`PlaylistScreen`).

Notas operativas
- Asegúrate de que todas las partes de la app que inician la reproducción desde una playlist (por ejemplo `PlaylistDetailScreen`) llamen a `playQueue(..., playlistName = name)` en vez de solo `playQueue(...)`. Si no se pasa `playlistName`, la app no podrá saber cuál es la playlist activa.
- Ten en cuenta que `activePlaylistName` es solo un puntero nominal: la información real de la playlist (lista de URIs) debe seguir guardándose en `PlaylistStore` y/o en la `queue` persistida.
- Para un comportamiento robusto, cuando se elimine o renombre una playlist, actualiza `activePlaylistName` si apunta a la playlist afectada (por ejemplo, clear o reemplazar el nombre).

Nuevo: menú de tres puntos en `PlaylistDetailScreen` (Edit / Delete / Add image)
-- Resumen funcional:
  - En la cabecera (`TopAppBar`) de `PlaylistDetailScreen` habrá un botón de "tres puntos" (overflow menu) en la parte superior derecha.
  - Opciones del menú:
    - Edit name: abre un diálogo para cambiar el nombre de la playlist. Al confirmar, renombrar en `PlaylistStore` y navegar a la lista de playlists (o refrescar la UI). Mantener la imagen asociada si existía (mover la entrada de imagen en `PlaylistStore`).
    - Delete playlist: pedir confirmación; al confirmar, borrar la playlist de `PlaylistStore`. Si la playlist borrada es la activa (`PlaybackController.activePlaylistName == name`), llamar a `PlaybackController.clearPlaybackAndReset()` para parar y vaciar el reproductor; luego navegar a la lista de playlists.
    - Add image: abrir el selector de documentos (`Intent.ACTION_OPEN_DOCUMENT`) filtrado a imágenes (`image/*`) mediante `ActivityResultContracts.OpenDocument()` o `OpenDocument` launcher. Al seleccionar:
      - Tomar permiso persistente: `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)` para poder acceder más tarde.
      - Guardar la URI como string en `PlaylistStore.setPlaylistImage(context, playlistName, uri.toString())`.
      - Actualizar la cabecera para mostrar la imagen recién seleccionada (cargar con `contentResolver.openInputStream(uri)` y decodificar bitmap para mostrar con Compose).

-- Implementación recomendada (snippets y notas técnicas):
- `PlaylistStore`:
  - Añadir funciones (ya añadidas en esta base):
    - `setPlaylistImage(context, name, uri: String)` — guarda en SharedPreferences/JSON el mapeo name->uri.
    - `getPlaylistImage(context, name): String?` — devuelve la URI guardada o null.
    - `deletePlaylist(...)` y `renamePlaylist(...)` deben limpiar/mover la entrada de imagen (ya implementado en `PlaylistStore`).

- `PlaylistDetailScreen` UI:
  - Añadir `IconButton` en `TopAppBar` con `Icons.Filled.MoreVert`.
  - Usar `DropdownMenu` para las tres opciones.
  - Para Add image usar:

```kotlin
val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
  uri?.let {
    // persist permission
    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    PlaylistStore.setPlaylistImage(context, playlistName, it.toString())
    currentArtworkUri = it.toString() // variable de estado para forzar recomposición
  }
}
imageLauncher.launch(arrayOf("image/*"))
```

- Para mostrar la imagen en la cabecera:
  - Intentar cargar `currentArtworkUri` con `contentResolver.openInputStream(uri)` y `BitmapFactory.decodeStream` → `asImageBitmap()` (usando `produceState` para la carga asincrónica) y dibujar con `Image(bitmap = ...)`.
  - Si `currentArtworkUri` es null o la carga falla, usar `R.drawable.playlist_default` como fallback.

-- Comportamiento al borrar una playlist activa
  - Ya explicado antes: al borrar la playlist activa, llamar a `PlaybackController.clearPlaybackAndReset()` y navegar a la lista.

-- Permisos y SAF
  - Al usar `OpenDocument`, debes pedir persistable permissions cuando recibes la URI. Esto permite volver a abrir la URI tras reinicios. Ejemplo:
    - `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
  - Guardar la URI (string) en `PlaylistStore` (SharedPreferences JSON) para reusar.

-- UX notes
  - Mostrar la imagen en la cabecera (alto ~180dp). El overflow menu debe cerrar automáticamente al escoger una acción.
  - Tras renombrar o borrar, navegar a la lista de playlists para evitar mostrar pantalla de detalle inexistente.

-- Playlist artwork synchronization (NEW requirement)
  - Requisito funcional:
    - La imagen que se muestra en `PlaylistDetailScreen` para una playlist debe ser exactamente la misma que aparece en la tarjeta de esa playlist en `PlayerListScreen`.
    - Si una playlist tiene una imagen almacenada, no debe mostrarse la imagen por defecto (`R.drawable.playlist_default`) en ninguna parte de la UI (ni en detalle ni en el listado).

  - Flujo recomendado de datos / responsabilidades:
    1. `PlaylistStore` es la única fuente de verdad para la URI de la imagen de cada playlist. Debe exponer claramente:
       - `fun setPlaylistImage(context: Context, name: String, uri: String)`
       - `fun getPlaylistImage(context: Context, name: String): String?`
       - `fun deletePlaylist(...)` y `fun renamePlaylist(...)` deben mover/limpiar la entrada de imagen apropiadamente.
    2. `PlaylistDetailScreen`:
       - Cuando el usuario añade/actualiza la imagen (SAF), llama `PlaylistStore.setPlaylistImage(...)` y actualiza su estado local `currentArtworkUri`.
       - Además, notifica a la UI de listado que actualice (ver opciones abajo).
    3. `PlayerListScreen` (lista de playlists):
       - Al componer cada tarjeta, en lugar de mostrar directamente `painterResource(R.drawable.playlist_default)`, consulta `PlaylistStore.getPlaylistImage(context, name)`:
         - Si devuelve una URI no nula y válida, cargar la imagen desde esa URI y mostrarla.
         - Si devuelve null o la carga falla, usar `R.drawable.playlist_default`.
       - Evitar mantener una copia local desincronizada de las URIs: recarga el valor (o observa una fuente de verdad) cuando se actualice una playlist.

  - Estrategias para actualizar la UI de listado cuando cambia la imagen:
    - Opciones (de más a menos recomendadas):
      A) Centralizar el estado de playlists en un ViewModel / StateFlow: `PlaylistsViewModel` expone la lista con metadatos (nombre, imageUri) y emite actualizaciones cuando `PlaylistStore` cambia. `PlaylistDetailScreen` al actualizar la imagen invoca el ViewModel para refrescar o el ViewModel escucha cambios en `PlaylistStore`.
      B) Emitir un resultado al volver de `PlaylistDetailScreen`: al hacer pop/navegar atrás, pasar un resultado (o usar `navController.previousBackStackEntry?.savedStateHandle`) para informar a `PlaylistScreen` de que recargue la lista.
      C) Como parche rápido y simple: después de `PlaylistStore.setPlaylistImage(...)` actualizar manualmente la lista en `PlayerListScreen` (p. ej. recargando `playlists = PlaylistStore.loadAll(context).keys.toList()` dentro de una effect/refresh callback).

  - Ejemplo de renderizado seguro en `PlayerListScreen` (pseudocódigo / idea):
    - En la lambda que genera cada tarjeta:
      - val imageUri = PlaylistStore.getPlaylistImage(context, name)
      - if (!imageUri.isNullOrBlank()) {
          // cargar de forma asincrónica con produceState/remember o usar Coil (AsyncImage)
          // show Image(bitmap = loadedBitmap)
        } else {
          Image(painter = painterResource(R.drawable.playlist_default), ...)
        }

  - Nota sobre rendimiento y librerías:
    - Cargar muchas imágenes mediante `BitmapFactory.decodeStream` en el hilo UI puede ser costoso. Para listas con muchas tarjetas, se recomienda usar una librería de carga de imágenes (Coil + coil-compose) que cachea y gestiona el threading:
      - Dependencia sugerida: `io.coil-kt:coil-compose` (si decides añadir dependencias en el futuro).
    - Si prefieres no añadir dependencias, usa `produceState` o `remember` para cargar `Bitmap` en un background thread y luego convertir a `ImageBitmap` con `asImageBitmap()`.

  - Comportamiento al renombrar/borrar playlists:
    - `renamePlaylist` debe mover la entrada de imagen (si existe) al nuevo nombre.
    - `deletePlaylist` debe eliminar la entrada de imagen asociada.
    - Si se está reproduciendo la playlist que se borra, limpiar el reproductor con `PlaybackController.clearPlaybackAndReset()` (ya documentado).

  - Pruebas manuales recomendadas:
    - Crear una playlist, añadir una imagen via `PlaylistDetailScreen`, volver al listado y comprobar que la miniatura de la tarjeta muestra la imagen nueva (no la default).
    - Cerrar y volver a abrir la app: comprobar que la imagen persiste (permiso persistente + URI guardada).
    - Renombrar la playlist: comprobar que la tarjeta en el listado mantiene la imagen tras el renombrado.
    - Borrar la playlist: comprobar que la imagen se elimina y que el reproductor queda vacío si era la activa.

  - Notas de migración (si usas DataStore en el futuro):
    - Si migras `PlaylistStore` a DataStore, expón un Flow<Map<String, PlaylistMeta>> que `PlaylistsViewModel` pueda observar y mapear a UI State. Esto hace la sincronización trivial.

12) Artwork embebido por pista (thumbnails)
 - Objetivo funcional:
   - Para cada pista (track) intentaremos leer la miniatura embebida (embedded artwork / ID3 APIC) desde el archivo de audio. Si existe, la mostraremos sobrepuesta encima de la imagen `song_default` (que sigue siendo el fondo). La miniatura aparecerá recortada en círculo y un poco más pequeña que `song_default`.
 - UX / tamaños recomendados:
   - `PlayerScreen`: `song_default` se muestra a 180dp; la miniatura embebida mostrarse centrada encima con un tamaño algo menor (p. ej. 140dp) y clipada a `CircleShape`.
   - `PlaybackMiniPlayer`: `song_default` se muestra a 48dp; la miniatura embebida mostrarse centrada encima con tamaño p. ej. 40dp y `CircleShape`.
   - Si no existe miniatura embebida, mostrar únicamente `song_default` (sin overlay).
 - Flujo técnico recomendado:
   1. Extraer la imagen embebida con `MediaMetadataRetriever.embeddedPicture` usando un helper que se ejecute en IO (no en UI thread). Devuelve `ImageBitmap?` o `null` si no existe o falla.
   2. Cargar la imagen de forma asincrónica en Compose con `produceState` o con `remember` + `LaunchedEffect` + `withContext(Dispatchers.IO)` para no bloquear la UI.
   3. En el composable que dibuja el artwork (tanto en `PlayerScreen` como en `PlaybackMiniPlayer`): usar un `Box` con `Image(painterResource(song_default))` y, si `embeddedBitmap != null`, dibujar encima `Image(bitmap = embeddedBitmap, modifier = Modifier.size(...).clip(CircleShape))` centrándola.
   4. Asegurar persistencia de permisos si la artwork proviene de `content://` URIs (SAF) y se necesita reabrir más adelante (takePersistableUriPermission); para archivos locales vía MediaStore no es necesario.
 - Implementación (ejemplo de helper, idea):
   - Helper (Kotlin suspending):
     - Abrir `ParcelFileDescriptor` desde `context.contentResolver.openFileDescriptor(uri, "r")` o usar direct path si está permitido.
     - Usar `MediaMetadataRetriever.setDataSource(fd)` y `retriever.embeddedPicture` para obtener bytes.
     - Decodificar con `BitmapFactory.decodeByteArray` y convertir a `ImageBitmap` con `asImageBitmap()`.
     - Siempre liberar `retriever.release()` y cerrar `ParcelFileDescriptor`.
   - En Compose (pseudocódigo):
     - val embedded = produceState<ImageBitmap?>(initialValue = null, key1 = audioUri) { value = loadEmbeddedArtwork(context, audioUri) }
     - Box { Image(painterResource(song_default)); if (embedded.value != null) Image(bitmap = embedded.value!!, modifier = Modifier.size(...).clip(CircleShape)) }
 - Rendimiento y librerías:
   - Si vas a mostrar miniaturas para muchas pistas (p. ej. en una lista), considera usar Coil (coil-compose) para caching y threading. Si no quieres añadir dependencias, asegúrate de cargar bitmaps en background y cachear en memoria (por ejemplo en un ViewModel) para evitar decodificaciones repetidas.
 - Fallback y errores:
   - Si la extracción falla por permisos o formato, registrar el fallo (Log) y mostrar `song_default` sin overlay.
   - No lanzar excepciones hacia la UI.


### Iconos 
- La librería de iconos utilizada es `androidx.compose.material.icons`. No debemos usar otras librerías de iconos externas ya que muchas están sin mantener o no son compatibles con Compose.
Ejemplos de iconos `androidx.compose.material.icons`:
- **PlayListDetailScreen.kt**:
  - `Icons.Filled.ArrowBack`
  - `Icons.Filled.Delete`
  - `Icons.Filled.Edit`
  - `Icons.Filled.Image`
  - `Icons.Filled.MoreVert`
- **PlayerScreen.kt**:
  - `Icons.Filled.ChevronLeft`
  - `Icons.Filled.ChevronRight`
  - `Icons.Filled.Pause`
  - `Icons.Filled.PlayArrow`
  - `Icons.Filled.Repeat`
  - `Icons.Filled.RepeatOne`
  - `Icons.Filled.Shuffle`
- **PlayerListScreen.kt**:
  - `Icons.Filled.Add`
  - `Icons.Filled.Delete`
  - `Icons.Filled.Edit`
  - `Icons.Filled.MoreVert`
