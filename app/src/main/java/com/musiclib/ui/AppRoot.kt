package com.musiclib.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musiclib.data.AppContainer
import com.musiclib.data.Track
import com.musiclib.playback.PlayerHolder
import kotlinx.coroutines.launch

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("songs", "Songs", Icons.Default.MusicNote),
    TabItem("playlists", "Playlists", Icons.Default.LibraryMusic),
    TabItem("queue", "Queue", Icons.AutoMirrored.Filled.PlaylistPlay),
    TabItem("downloaders", "Uploads", Icons.Default.Upload),
    TabItem("downloads", "Downloads", Icons.Default.Download),
    TabItem("settings", "Settings", Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    container: AppContainer,
    player: PlayerHolder,
    onPlay: (Track) -> Unit,
    onPlayList: (tracks: List<Track>, startIndex: Int) -> Unit,
    onEnqueue: (Track) -> Unit,
    onEnqueueAll: (List<Track>) -> Unit,
) {
    val nav = rememberNavController()
    val settings by container.settings.flow.collectAsState(initial = null)

    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        val current = nav.currentDestination?.route
        if (!s.isConfigured && current != "settings") {
            nav.navigate("settings") { popUpTo("songs") { inclusive = true } }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun openDrawer() = scope.launch { drawerState.open() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                TABS.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any {
                        it.route == tab.route || it.route?.startsWith("${tab.route}/") == true
                    } == true
                    NavigationDrawerItem(
                        label = { Text(tab.label) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        selected = selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentRoute != tab.route) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { MiniPlayer(player = player) },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "songs",
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                composable("songs") {
                    // Each destination collects settings independently so the
                    // current libraryId is always fresh on recomposition, not a
                    // value captured at NavHost construction time.
                    val s by container.settings.flow.collectAsState(initial = null)
                    val libraryId = s?.selectedLibraryId ?: 0L
                    val vm: SongsViewModel = viewModel(
                        key = "songs-$libraryId",
                        factory = viewModelFactory {
                            initializer { SongsViewModel(container.api, container.database.cacheDao(), libraryId) }
                        },
                    )
                    SongsScreen(
                        viewModel = vm,
                        libraryId = libraryId,
                        onPlay = onPlay,
                        onEnqueue = onEnqueue,
                        onEnqueueAll = onEnqueueAll,
                        container = container,
                        onMenuClick = { openDrawer() },
                    )
                }
                composable("playlists") {
                    val s by container.settings.flow.collectAsState(initial = null)
                    val libraryId = s?.selectedLibraryId ?: 0L
                    PlaylistsListScreen(
                        container = container,
                        libraryId = libraryId,
                        onOpen = { id -> nav.navigate("playlists/$id") },
                        onMenuClick = { openDrawer() },
                    )
                }
                composable("playlists/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull()
                    if (id != null) {
                        val s by container.settings.flow.collectAsState(initial = null)
                        val libraryId = s?.selectedLibraryId ?: 0L
                        PlaylistDetailScreen(
                            container = container,
                            libraryId = libraryId,
                            playlistId = id,
                            onPlayPlaylist = onPlayList,
                            onEnqueueTrack = onEnqueue,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("queue") {
                    QueueScreen(player = player, onMenuClick = { openDrawer() })
                }
                composable("settings") {
                    SettingsScreen(
                        repo = container.settings,
                        api = container.api,
                        onSaved = {
                            if (!nav.popBackStack()) {
                                nav.navigate("songs") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            }
                        },
                        onBack = null,
                        onMenuClick = { openDrawer() },
                    )
                }
                composable("downloads") {
                    val s by container.settings.flow.collectAsState(initial = null)
                    val libraryId = s?.selectedLibraryId ?: 0L
                    DownloadsScreen(
                        container = container,
                        libraryId = libraryId,
                        onMenuClick = { openDrawer() },
                    )
                }
                composable("downloaders") {
                    val s by container.settings.flow.collectAsState(initial = null)
                    val libraryId = s?.selectedLibraryId ?: 0L
                    DownloadersScreen(
                        container = container,
                        libraryId = libraryId,
                        onMenuClick = { openDrawer() },
                    )
                }
            }
        }
    }
}
