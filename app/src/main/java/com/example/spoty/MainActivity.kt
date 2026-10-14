// ============================================================
// ИМПОРТЫ
// ============================================================
package com.example.spoty

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spoty.data.AlbumArt
import com.example.spoty.data.FolderScanner
import com.example.spoty.data.MusicScanner
import com.example.spoty.data.Playlist
import com.example.spoty.data.PlaylistStore
import com.example.spoty.data.Song
import com.example.spoty.data.ThemeStore
import com.example.spoty.player.PlayerManager
import com.example.spoty.player.VolumeController
import com.example.spoty.ui.theme.LocalSpotyColors
import com.example.spoty.ui.theme.SpotyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================
// АКТИВИТИ + ТЕМА
// ============================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this
            // Тема хранится в SharedPreferences, читаем при старте.
            var isDark by remember { mutableStateOf(ThemeStore.isDark(context)) }
            SpotyTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalSpotyColors.current.background
                ) {
                    AppRoot(
                        isDark = isDark,
                        onToggleTheme = {
                            isDark = !isDark
                            ThemeStore.setDark(context, isDark)
                        }
                    )
                }
            }
        }
    }
}

// ============================================================
// КОРНЕВОЙ КОМПОНЕНТ (всё состояние приложения тут)
// ============================================================
@Composable
fun AppRoot(
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSpotyColors.current

    // Плеер и громкость живут на уровне AppRoot, чтобы не сбрасываться
    // при переключении вкладок.
    val playerManager = remember { PlayerManager(context) }
    val volumeController = remember { VolumeController(context) }

    DisposableEffect(Unit) {
        onDispose { playerManager.release() }
    }

    // --- Состояния ---
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }

    var isShuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(PlayerManager.REPEAT_ALL) }

    val maxVolume = remember { volumeController.getMaxVolume() }
    var currentVolume by remember { mutableIntStateOf(volumeController.getCurrentVolume()) }
    var showVolumeSlider by remember { mutableStateOf(ThemeStore.isShowVolume(context)) }

    var playlists by remember { mutableStateOf(PlaylistStore.load(context)) }

    // Очередь воспроизведения — какой список сейчас играет (вся библиотека или плейлист).
    // Нужна для next/prev и для поиска песни по mediaId из шторки.
    var playbackQueue by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Какой трек сейчас показывать в BottomSheet «в плейлист».
    var sheetSong by remember { mutableStateOf<Song?>(null) }

    // Раз в 300 мс читаем системную громкость — чтобы ползунок двигался,
    // если юзер нажал физические кнопки.
    LaunchedEffect(Unit) {
        while (true) {
            val v = volumeController.getCurrentVolume()
            if (v != currentVolume) currentVolume = v
            delay(300)
        }
    }

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Синхронизация флагов с ExoPlayer (shuffle и repeat уходят внутрь плеера).
    LaunchedEffect(isShuffle) { playerManager.setShuffle(isShuffle) }
    LaunchedEffect(repeatMode) { playerManager.setRepeatMode(repeatMode) }

    // Подписка на события PlayerManager: сюда прилетают изменения,
    // сделанные из шторки. Это ключевой момент синхронизации UI ↔ шторка.
    DisposableEffect(Unit) {
        playerManager.queueProvider = { playbackQueue }
        playerManager.onSongChanged = { song -> if (song != null) currentSong = song }
        playerManager.onPlayingChanged = { playing -> isPlaying = playing }
        onDispose {
            playerManager.queueProvider = null
            playerManager.onSongChanged = null
            playerManager.onPlayingChanged = null
        }
    }

    // --- Разрешения ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) songs = MusicScanner.scanAllSongs(context)
    }

    // Выбор папки через системный диалог.
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                // Persistable — чтобы доступ к папке сохранился после перезапуска.
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            songs = FolderScanner.scanFolder(context, uri)
            Toast.makeText(context, "Найдено треков: ${songs.size}", Toast.LENGTH_SHORT).show()
        }
    }

    // При первом запуске запрашиваем разрешение на аудио.
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        val granted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) {
            hasPermission = true
            songs = MusicScanner.scanAllSongs(context)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // Обновляем позицию трека каждые 500 мс, пока что-то играет.
    LaunchedEffect(currentSong, isPlaying) {
        while (currentSong != null) {
            if (!isSeeking) {
                positionMs = playerManager.getCurrentPosition()
                durationMs = playerManager.getDuration()
            }
            delay(500)
        }
    }

    // --- Хелперы ---
    fun playNext() {
        // Если есть очередь — пусть ExoPlayer сам переключит (учитывая shuffle/repeat).
        if (playbackQueue.isNotEmpty()) {
            playerManager.skipToNext()
        } else {
            val next = playerManager.nextSong(songs) ?: return
            playerManager.playSong(next)
            currentSong = next
            isPlaying = true
        }
    }
    fun playPrevious() {
        if (playbackQueue.isNotEmpty()) {
            playerManager.skipToPrevious()
        } else {
            val prev = playerManager.previousSong(songs) ?: return
            playerManager.playSong(prev)
            currentSong = prev
            isPlaying = true
        }
    }
    // Запуск трека из всей библиотеки — очередь = весь список.
    fun playSongFromAll(song: Song) {
        playbackQueue = songs
        playerManager.playQueue(songs, song)
        currentSong = song
        isPlaying = true
        showFullPlayer = true
    }
    // Запуск трека из плейлиста — очередь = треки плейлиста.
    fun playSongFromPlaylist(queue: List<Song>, song: Song) {
        playbackQueue = queue
        playerManager.playQueue(queue, song)
        currentSong = song
        isPlaying = true
        showFullPlayer = true
    }

    // ========================================================
    // UI
    // ========================================================
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                Column {
                    // Мини-плеер над навигацией — виден на всех вкладках.
                    currentSong?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            onClick = { showFullPlayer = true },
                            onSwipeUp = { showFullPlayer = true },
                            onTogglePlay = { playerManager.togglePlayPause() }
                        )
                    }

                    NavigationBar(
                        containerColor = colors.surface,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "Главная") },
                            label = { Text("Главная") },
                            colors = navItemColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Filled.Search, contentDescription = "Поиск") },
                            label = { Text("Поиск") },
                            colors = navItemColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Filled.QueueMusic, contentDescription = "Плейлисты") },
                            label = { Text("Плейлисты") },
                            colors = navItemColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Настройки") },
                            label = { Text("Настройки") },
                            colors = navItemColors()
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        songs = songs,
                        hasPermission = hasPermission,
                        playlists = playlists,
                        onScan = { songs = MusicScanner.scanAllSongs(context) },
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        onSongClick = { playSongFromAll(it) },
                        onDeleteSong = { song ->
                            // Удаление только из UI. Файл на телефоне остаётся.
                            songs = songs.filterNot { it.id == song.id }
                            if (currentSong?.id == song.id) {
                                playerManager.release()
                                currentSong = null
                                isPlaying = false
                                showFullPlayer = false
                            }
                        },
                        onTogglePlaylistSong = { playlistId, songId ->
                            playlists = PlaylistStore.toggleSong(context, playlistId, songId)
                        }
                    )
                    1 -> SearchScreen(
                        songs = songs,
                        onSongClick = { playSongFromAll(it) }
                    )
                    2 -> PlaylistsScreen(
                        songs = songs,
                        playlists = playlists,
                        onCreate = { name -> playlists = PlaylistStore.create(context, name) },
                        onDelete = { id -> playlists = PlaylistStore.delete(context, id) },
                        onPlayPlaylistSong = { queue, song -> playSongFromPlaylist(queue, song) },
                        onRemoveSong = { playlistId, songId ->
                            playlists = PlaylistStore.toggleSong(context, playlistId, songId)
                        }
                    )
                    3 -> SettingsScreen(
                        isDark = isDark,
                        showVolumeSlider = showVolumeSlider,
                        onToggleTheme = onToggleTheme,
                        onToggleVolumeSlider = {
                            showVolumeSlider = !showVolumeSlider
                            ThemeStore.setShowVolume(context, showVolumeSlider)
                        },
                        onScan = {
                            songs = MusicScanner.scanAllSongs(context)
                            Toast.makeText(context, "Сканирование…", Toast.LENGTH_SHORT).show()
                        },
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        onClearList = {
                            songs = emptyList()
                            playerManager.release()
                            currentSong = null
                            isPlaying = false
                            showFullPlayer = false
                            Toast.makeText(context, "Плейлист очищен", Toast.LENGTH_SHORT).show()
                        },
                        onClearPlaylists = {
                            playlists = emptyList()
                            PlaylistStore.save(context, emptyList())
                            Toast.makeText(context, "Плейлисты удалены", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Полноэкранный плеер выезжает снизу поверх всего.
        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
                PlayerScreen(
                    song = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    currentVolume = currentVolume,
                    maxVolume = maxVolume,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    showVolumeSlider = showVolumeSlider,
                    onClose = { showFullPlayer = false },
                    onTogglePlay = { playerManager.togglePlayPause() },
                    onNext = { playNext() },
                    onPrevious = { playPrevious() },
                    onToggleShuffle = { isShuffle = !isShuffle },
                    onCycleRepeat = {
                        repeatMode = when (repeatMode) {
                            PlayerManager.REPEAT_OFF -> PlayerManager.REPEAT_ALL
                            PlayerManager.REPEAT_ALL -> PlayerManager.REPEAT_ONE
                            else -> PlayerManager.REPEAT_OFF
                        }
                    },
                    onSeek = { newPos ->
                        playerManager.seekTo(newPos)
                        positionMs = newPos
                    },
                    onSeekingChanged = { seeking -> isSeeking = seeking },
                    onVolumeChange = { v ->
                        currentVolume = v
                        volumeController.setVolume(v)
                    },
                    onAddToPlaylist = { song -> sheetSong = song }
                )
            }
        }

        // BottomSheet «Добавить в плейлист», вызывается плюсиком.
        sheetSong?.let { song ->
            AddToPlaylistSheet(
                song = song,
                playlists = playlists,
                onDismiss = { sheetSong = null },
                onPick = { playlistId ->
                    playlists = PlaylistStore.toggleSong(context, playlistId, song.id)
                    Toast.makeText(context, "Обновлено", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = LocalSpotyColors.current.textPrimary,
    selectedTextColor = LocalSpotyColors.current.textPrimary,
    unselectedIconColor = LocalSpotyColors.current.textHint,
    unselectedTextColor = LocalSpotyColors.current.textHint,
    indicatorColor = LocalSpotyColors.current.surface3
)

// ============================================================
// ГЛАВНАЯ — список всех песен
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    hasPermission: Boolean,
    playlists: List<Playlist>,
    onScan: () -> Unit,
    onPickFolder: () -> Unit,
    onSongClick: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onTogglePlaylistSong: (String, Long) -> Unit
) {
    val colors = LocalSpotyColors.current
    var showAddMenu by remember { mutableStateOf(false) }
    var sheetSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Шапка: заголовок и кнопка "+" (сканирование)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Spoty",
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddMenu = !showAddMenu }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить музыку", tint = colors.textPrimary)
            }
        }

        // Выпадающее меню "Сканировать / Выбрать папку"
        AnimatedVisibility(visible = showAddMenu) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
            ) {
                AddMenuRow(
                    icon = Icons.Filled.Search,
                    text = "Сканировать телефон",
                    onClick = { showAddMenu = false; onScan() }
                )
                HorizontalDivider(color = colors.divider)
                AddMenuRow(
                    icon = Icons.Filled.Folder,
                    text = "Выбрать папку вручную",
                    onClick = { showAddMenu = false; onPickFolder() }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasPermission -> CenteredText("Нет разрешения на чтение музыки")
                songs.isEmpty() -> CenteredText("Песни не найдены. Нажми «+» сверху")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SwipeableSongRow(
                            song = song,
                            onClick = { onSongClick(song) },
                            onDelete = { onDeleteSong(song) },
                            onAddToPlaylist = { sheetSong = song }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    sheetSong?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            onDismiss = { sheetSong = null },
            onPick = { playlistId ->
                onTogglePlaylistSong(playlistId, song.id)
                Toast.makeText(context, "Обновлено", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ============================================================
// BOTTOM SHEET "ДОБАВИТЬ В ПЛЕЙЛИСТ"
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val colors = LocalSpotyColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textHint)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Добавить в плейлист",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(song.title, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Text(
                    "Плейлистов пока нет. Создай их на вкладке «Плейлисты».",
                    color = colors.textHint,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        val isIn = p.songIds.contains(song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(p.id) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("${p.songIds.size} треков", color = colors.textHint, fontSize = 12.sp)
                            }
                            // Галочка — если трек уже в этом плейлисте.
                            if (isIn) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Уже в плейлисте",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// ПОИСК
// ============================================================
@Composable
fun SearchScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    val colors = LocalSpotyColors.current
    var query by remember { mutableStateOf("") }

    // Фильтрация идёт по трём полям сразу.
    val filtered = remember(query, songs) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Поиск", color = colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Название, артист или альбом", color = colors.textHint) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.textSecondary,
                unfocusedBorderColor = colors.divider,
                cursorColor = colors.textPrimary,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        when {
            songs.isEmpty() -> CenteredText("Нет песен. Сначала просканируй музыку на главной.")
            filtered.isEmpty() -> CenteredText("Ничего не найдено по запросу «$query»")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { song ->
                    SimpleSongRow(song = song, onClick = { onSongClick(song) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ============================================================
// ПЛЕЙЛИСТЫ — список + полноэкранная деталь
// ============================================================
@Composable
fun PlaylistsScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPlayPlaylistSong: (List<Song>, Song) -> Unit,
    onRemoveSong: (String, Long) -> Unit
) {
    val colors = LocalSpotyColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var openedPlaylistId by remember { mutableStateOf<String?>(null) }

    // Если плейлист открыт — показываем его полноэкранно и выходим из функции.
    val openedPlaylist = openedPlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }
    if (openedPlaylist != null) {
        val playlistSongs = songs.filter { openedPlaylist.songIds.contains(it.id) }
        PlaylistDetailScreen(
            playlist = openedPlaylist,
            playlistSongs = playlistSongs,
            onBack = { openedPlaylistId = null },
            onSongClick = { song -> onPlayPlaylistSong(playlistSongs, song) },
            onRemoveSong = { songId -> onRemoveSong(openedPlaylist.id, songId) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Плейлисты",
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Создать", tint = colors.textPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            CenteredText("Плейлистов нет. Нажми «+» сверху, чтобы создать.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { p ->
                    SwipeablePlaylistRow(
                        playlist = p,
                        onClick = { openedPlaylistId = p.id },
                        onDelete = { onDelete(p.id) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    // Диалог создания плейлиста.
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новый плейлист", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Название", color = colors.textHint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.textSecondary,
                        unfocusedBorderColor = colors.divider,
                        cursorColor = colors.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onCreate(newName.trim())
                        newName = ""
                        showCreateDialog = false
                    }
                }) { Text("Создать", color = colors.textPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена", color = colors.textHint)
                }
            },
            containerColor = colors.surface
        )
    }
}

// ============================================================
// СВАЙП-СТРОКА ПЛЕЙЛИСТА (в общем списке)
// ============================================================
@Composable
fun SwipeablePlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalSpotyColors.current
    val density = LocalDensity.current
    val openWidthDp = 80.dp
    val openWidthPx = with(density) { openWidthDp.toPx() }

    // offsetX — насколько строка сдвинута влево. 0 = закрыта, -80dp = открыта.
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isOpen by remember { derivedStateOf { offsetX.value < -openWidthPx / 2f } }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            // Ограничиваем сдвиг диапазоном [-openWidthPx, 0].
            val newValue = (offsetX.value + delta).coerceIn(-openWidthPx, 0f)
            offsetX.snapTo(newValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
    ) {
        // Красный фон с корзиной — виден, когда строка сдвинута.
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFE53935)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(openWidthDp)
                    .fillMaxHeight()
                    .clickable {
                        scope.launch { offsetX.animateTo(0f) }
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Удалить",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Сама строка плейлиста, которая сдвигается.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        scope.launch {
                            // "Прилипаем" к крайнему положению в зависимости от того,
                            // куда сдвинули — за половину или нет.
                            if (offsetX.value < -openWidthPx / 2f) {
                                offsetX.animateTo(-openWidthPx)
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    }
                )
                .clickable {
                    if (isOpen) {
                        // Если открыта — тап закрывает.
                        scope.launch { offsetX.animateTo(0f) }
                    } else {
                        onClick()
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${playlist.songIds.size} треков",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// ПОЛНОЭКРАННЫЙ ЭКРАН ПЛЕЙЛИСТА
// ============================================================
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    playlistSongs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Long) -> Unit
) {
    val colors = LocalSpotyColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Назад", tint = colors.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text("${playlistSongs.size} треков", color = colors.textSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (playlistSongs.isEmpty()) {
            CenteredText("Плейлист пуст. Свайпни трек на главной и нажми плюсик, чтобы добавить.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlistSongs, key = { it.id }) { song ->
                    SwipeableRemoveSongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onRemove = { onRemoveSong(song.id) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// Свайп-строка трека внутри плейлиста — свайп влево убирает трек.
@Composable
fun SwipeableRemoveSongRow(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalSpotyColors.current
    val density = LocalDensity.current
    val openWidthDp = 80.dp
    val openWidthPx = with(density) { openWidthDp.toPx() }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isOpen by remember { derivedStateOf { offsetX.value < -openWidthPx / 2f } }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val newValue = (offsetX.value + delta).coerceIn(-openWidthPx, 0f)
            offsetX.snapTo(newValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFE53935)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(openWidthDp)
                    .fillMaxHeight()
                    .clickable {
                        scope.launch { offsetX.animateTo(0f) }
                        onRemove()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Убрать",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        scope.launch {
                            if (offsetX.value < -openWidthPx / 2f) {
                                offsetX.animateTo(-openWidthPx)
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    }
                )
                .clickable {
                    if (isOpen) {
                        scope.launch { offsetX.animateTo(0f) }
                    } else {
                        onClick()
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumCover(albumId = song.albumId, size = 48.dp, corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, color = colors.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(song.artist, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

// ============================================================
// НАСТРОЙКИ
// ============================================================
@Composable
fun SettingsScreen(
    isDark: Boolean,
    showVolumeSlider: Boolean,
    onToggleTheme: () -> Unit,
    onToggleVolumeSlider: () -> Unit,
    onScan: () -> Unit,
    onPickFolder: () -> Unit,
    onClearList: () -> Unit,
    onClearPlaylists: () -> Unit
) {
    val colors = LocalSpotyColors.current
    var showAbout by remember { mutableStateOf(false) }
    var showClearList by remember { mutableStateOf(false) }
    var showClearPlaylists by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Шапка: иконка + подпись по центру.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text("Spotify", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        // Тумблеры.
        SettingsToggleRow("Тёмная тема", isDark, onToggleTheme)
        SettingsToggleRow("Показывать ползунок громкости", showVolumeSlider, onToggleVolumeSlider)

        Spacer(Modifier.height(8.dp))

        // Действия.
        SettingsRow("Сканировать телефон", Icons.Filled.Search) { onScan() }
        SettingsRow("Выбрать папку вручную", Icons.Filled.Folder) { onPickFolder() }
        SettingsRow("Очистить плейлист", Icons.Filled.Delete) { showClearList = true }
        SettingsRow("Удалить все плейлисты", Icons.Filled.Delete) { showClearPlaylists = true }

        Spacer(Modifier.weight(1f))

        SettingsRow("О приложении", Icons.Filled.Home) { showAbout = true }

        Text(
            "Spoty v1.0",
            color = colors.textHint,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("О приложении", color = colors.textPrimary) },
            text = {
                Text(
                    "Spoty — простой mp3-плеер.\n\nВерсия: 1.0\n\nФункции: сканирование музыки, плейлисты, темы, поиск.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("ОК", color = colors.textPrimary) }
            },
            containerColor = colors.surface
        )
    }

    if (showClearList) {
        ConfirmDialog(
            title = "Очистить плейлист?",
            text = "Все треки уберутся из списка. Файлы на телефоне останутся.",
            onConfirm = { showClearList = false; onClearList() },
            onDismiss = { showClearList = false }
        )
    }

    if (showClearPlaylists) {
        ConfirmDialog(
            title = "Удалить все плейлисты?",
            text = "Это действие нельзя отменить.",
            onConfirm = { showClearPlaylists = false; onClearPlaylists() },
            onDismiss = { showClearPlaylists = false }
        )
    }
}

@Composable
fun SettingsRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalSpotyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = colors.textPrimary, fontSize = 15.sp)
    }
}

@Composable
fun SettingsToggleRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalSpotyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.textPrimary,
                uncheckedThumbColor = colors.textHint,
                uncheckedTrackColor = colors.surface3
            )
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSpotyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colors.textPrimary) },
        text = { Text(text, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Да", color = Color(0xFFE53935)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = colors.textPrimary) }
        },
        containerColor = colors.surface
    )
}

// ============================================================
// ОБЩИЕ КОМПОНЕНТЫ
// ============================================================
@Composable
fun AddMenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = LocalSpotyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = colors.textPrimary, fontSize = 14.sp)
    }
}

@Composable
fun CenteredText(text: String) {
    val colors = LocalSpotyColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = colors.textHint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

// Асинхронно загружает обложку и запоминает её по albumId.
@Composable
fun rememberAlbumArt(albumId: Long): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(albumId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(albumId) {
        bitmap = withContext(Dispatchers.IO) { AlbumArt.load(context, albumId) }
    }
    return bitmap
}

@Composable
fun AlbumCover(albumId: Long, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    val colors = LocalSpotyColors.current
    val bitmap = rememberAlbumArt(albumId)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(colors.surface3)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SongRow(song: Song) {
    val colors = LocalSpotyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(albumId = song.albumId, size = 48.dp, corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title, color = colors.textPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Text(song.artist, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun SimpleSongRow(song: Song, onClick: () -> Unit) {
    val colors = LocalSpotyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(albumId = song.albumId, size = 48.dp, corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title, color = colors.textPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Text(song.artist, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

// Свайп-строка с ДВУМЯ кнопками: плюсик (в плейлист) и корзина (удалить).
@Composable
fun SwipeableSongRow(
    song: Song,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val colors = LocalSpotyColors.current
    val density = LocalDensity.current
    val buttonWidthDp = 80.dp
    val totalWidthDp = 160.dp
    val totalWidthPx = with(density) { totalWidthDp.toPx() }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isOpen by remember { derivedStateOf { offsetX.value < -totalWidthPx / 2f } }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val newValue = (offsetX.value + delta).coerceIn(-totalWidthPx, 0f)
            offsetX.snapTo(newValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Серая кнопка с плюсиком — добавление в плейлист.
            Box(
                modifier = Modifier
                    .width(buttonWidthDp)
                    .fillMaxHeight()
                    .background(colors.surface3)
                    .clickable {
                        scope.launch { offsetX.animateTo(0f) }
                        onAddToPlaylist()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistAdd,
                    contentDescription = "В плейлист",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            // Красная кнопка с корзиной — удаление.
            Box(
                modifier = Modifier
                    .width(buttonWidthDp)
                    .fillMaxHeight()
                    .background(Color(0xFFE53935))
                    .clickable {
                        scope.launch { offsetX.animateTo(0f) }
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Удалить",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Сама строка, которая сдвигается.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        scope.launch {
                            if (offsetX.value < -totalWidthPx / 2f) {
                                offsetX.animateTo(-totalWidthPx)
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    }
                )
                .clickable {
                    if (isOpen) {
                        scope.launch { offsetX.animateTo(0f) }
                    } else {
                        onClick()
                    }
                }
        ) {
            SongRow(song = song)
        }
    }
}

// ============================================================
// МИНИ-ПЛЕЕР (над нижней навигацией, на всех вкладках)
// ============================================================
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onTogglePlay: () -> Unit
) {
    val colors = LocalSpotyColors.current
    // Свайп вверх по мини-плееру открывает полный плеер.
    val dragState = rememberDraggableState { delta ->
        if (delta < -30f) onSwipeUp()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.surface2)
            .draggable(state = dragState, orientation = Orientation.Vertical)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(albumId = song.albumId, size = 36.dp, corner = 6.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title, color = colors.textPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Text(song.artist, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.textPrimary
            )
        }
    }
}

// ============================================================
// ПОЛНОЭКРАННЫЙ ПЛЕЕР
// ============================================================
@Composable
fun PlayerScreen(
    song: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    currentVolume: Int,
    maxVolume: Int,
    isShuffle: Boolean,
    repeatMode: Int,
    showVolumeSlider: Boolean,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val colors = LocalSpotyColors.current
    // Свайп вниз закрывает полный плеер.
    val dragState = rememberDraggableState { delta ->
        if (delta > 30f) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .draggable(state = dragState, orientation = Orientation.Vertical)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Верхняя панель: стрелка вниз + заголовок.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Свернуть", tint = colors.textPrimary)
            }
            Spacer(Modifier.weight(1f))
            Text("Сейчас играет", color = colors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(24.dp))

        // Большая обложка.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface3)
        ) {
            if (song != null) {
                val bitmap = rememberAlbumArt(song.albumId)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Название и артист.
        Text(
            text = song?.title ?: "Ничего не играет",
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = song?.artist ?: "",
            color = colors.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        // Прогресс-бар трека + время.
        val safeDuration = if (durationMs > 0) durationMs else 1L
        Slider(
            value = positionMs.toFloat().coerceIn(0f, safeDuration.toFloat()),
            onValueChange = { newValue ->
                onSeekingChanged(true)
                onSeek(newValue.toLong())
            },
            onValueChangeFinished = { onSeekingChanged(false) },
            valueRange = 0f..safeDuration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.textPrimary,
                inactiveTrackColor = colors.surface3
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(positionMs), color = colors.textSecondary, fontSize = 12.sp)
            Text(formatTime(durationMs), color = colors.textSecondary, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Ряд управления: shuffle | prev | play | next | repeat.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Перемешать",
                    tint = if (isShuffle) colors.textPrimary else colors.textHint,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Prev",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(76.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
                // Иконка и цвет зависят от текущего режима повтора.
                val icon = if (repeatMode == PlayerManager.REPEAT_ONE)
                    Icons.Filled.RepeatOne else Icons.Filled.Repeat
                val tint = if (repeatMode == PlayerManager.REPEAT_OFF)
                    colors.textHint else colors.textPrimary
                Icon(
                    imageVector = icon,
                    contentDescription = "Повтор",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Отдельная строка с плюсиком — добавить в плейлист.
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { song?.let { onAddToPlaylist(it) } },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistAdd,
                    contentDescription = "В плейлист",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Ползунок громкости — показывается, если включено в настройках.
        if (showVolumeSlider) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.VolumeDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..maxVolume.toFloat(),
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.textPrimary,
                        activeTrackColor = colors.textPrimary,
                        inactiveTrackColor = colors.surface3
                    )
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ============================================================
// ФОРМАТ ВРЕМЕНИ: 0:00 / 2:33
// ============================================================
fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}