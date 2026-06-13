package com.nndd_re.ui.screen.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.nndd_re.data.repository.NnddRepository
import com.nndd_re.ui.SharedViewModel
import com.nndd_re.ui.common.VideoCard
import com.nndd_re.ui.common.VideoListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repo: NnddRepository,
    onVideoClick: (String) -> Unit,
    onSettings: () -> Unit
) {
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val sharedVm: SharedViewModel = viewModel(viewModelStoreOwner = activity ?: return)

    val vm: LibraryViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LibraryViewModel(repo) as T
            }
        }
    )

    val state by vm.state.collectAsState()
    val selectedFolder by vm.selectedFolder.collectAsState()
    val selectedTag by vm.selectedTag.collectAsState()
    val sortField by vm.sortField.collectAsState()
    val sortOrder by vm.sortOrder.collectAsState()
    val viewMode by vm.viewMode.collectAsState()

    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val successState = state as? LibraryUiState.Success

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "ライブラリ",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()

                successState?.let { s ->
                    val folders = remember(s.videos) {
                        s.videos.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
                    }
                    val allTags = remember(s.videos) {
                        s.videos.flatMap { it.tags }.distinct().sorted()
                    }

                    LazyColumn {
                        // すべて
                        item {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                label = { Text("すべて") },
                                selected = selectedFolder == null && selectedTag == null,
                                onClick = {
                                    vm.setFolder(null)
                                    vm.setTag(null)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        // フォルダー
                        if (folders.isNotEmpty()) {
                            item {
                                Text(
                                    "フォルダー",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(folders) { f ->
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    label = { Text(f, maxLines = 1) },
                                    selected = selectedFolder == f && selectedTag == null,
                                    onClick = {
                                        vm.setFolder(if (selectedFolder == f) null else f)
                                        vm.setTag(null)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }

                        // タグ
                        if (allTags.isNotEmpty()) {
                            item {
                                Text(
                                    "タグ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(allTags) { t ->
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                                    label = { Text(t, maxLines = 1) },
                                    selected = selectedTag == t,
                                    onClick = {
                                        vm.setTag(if (selectedTag == t) null else t)
                                        vm.setFolder(null)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (searchActive) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("タイトル・タグで検索") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                searchActive = false
                                query = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "検索を閉じる")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            // 現在のフィルター状態をタイトルに表示
                            val subtitle = when {
                                selectedFolder != null -> selectedFolder
                                selectedTag != null -> "#$selectedTag"
                                else -> null
                            }
                            Column {
                                var titleFontSize by remember { mutableStateOf(22.sp) }
                                Text(
                                    "ライブラリ",
                                    fontSize = titleFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { if (it.hasVisualOverflow) titleFontSize *= 0.9f }
                                )
                                subtitle?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "メニュー")
                            }
                        },
                        actions = {
                            // ソートボタン
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "ソート")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortField.entries.forEach { field ->
                                        val isSelected = sortField == field
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        field.label,
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            if (sortOrder == SortOrder.DESC)
                                                                Icons.Default.ArrowDownward
                                                            else
                                                                Icons.Default.ArrowUpward,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                vm.setSort(field)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // グリッド/リスト切り替え
                            IconButton(onClick = { vm.toggleViewMode() }) {
                                Icon(
                                    if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (viewMode == ViewMode.GRID) "リスト表示" else "グリッド表示"
                                )
                            }

                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "検索")
                            }
                            IconButton(onClick = { vm.load() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "更新")
                            }
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "設定")
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (val s = state) {
                    is LibraryUiState.Loading ->
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                    is LibraryUiState.Error ->
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(s.message, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { vm.load() }) { Text("再試行") }
                            }
                        }

                    is LibraryUiState.Success -> {
                        val filtered = remember(s.videos, selectedFolder, selectedTag, query) {
                            s.videos.filter { v ->
                                (selectedFolder == null || v.folder == selectedFolder) &&
                                (selectedTag == null || v.tags.contains(selectedTag)) &&
                                (query.isBlank() ||
                                    v.videoName.contains(query, ignoreCase = true) ||
                                    v.tags.any { it.contains(query, ignoreCase = true) })
                            }
                        }

                        val sorted = remember(filtered, sortField, sortOrder) {
                            val comparator: Comparator<com.nndd_re.data.model.NnddVideo> = when (sortField) {
                                SortField.PUB_DATE -> compareBy { it.pubDate ?: it.creationDate ?: "" }
                                SortField.VIDEO_NAME -> compareBy { it.videoName }
                                SortField.PLAY_COUNT -> compareBy { it.playCount }
                                SortField.DURATION -> compareBy { it.duration }
                                SortField.LAST_PLAY -> compareBy { it.lastPlayDate ?: "" }
                            }
                            val ordered = if (sortOrder == SortOrder.DESC) comparator.reversed() else comparator
                            filtered.sortedWith(ordered)
                        }

                        if (sorted.isEmpty()) {
                            Box(Modifier.fillMaxSize()) {
                                Text(
                                    if (query.isNotBlank()) "「$query」に一致する動画はありません"
                                    else "該当する動画はありません",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        } else if (viewMode == ViewMode.GRID) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sorted, key = { it.id }) { video ->
                                    val idKey = video.videoId ?: video.id.toString()
                                    val ctx = LocalContext.current
                                    VideoCard(
                                        video = video,
                                        thumbUrl = vm.thumbUrl(idKey),
                                        onClick = {
                                            sharedVm.put(idKey, video)
                                            onVideoClick(idKey)
                                        },
                                        onOpenInNicovideo = {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nicovideo.jp/watch/$idKey")))
                                            } catch (_: ActivityNotFoundException) {}
                                        },
                                        onOpenInNicobox = {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sp.nicovideo.jp/force-app-link/watch/$idKey")))
                                            } catch (_: ActivityNotFoundException) {}
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sorted, key = { it.id }) { video ->
                                    val idKey = video.videoId ?: video.id.toString()
                                    val ctx = LocalContext.current
                                    VideoListItem(
                                        video = video,
                                        thumbUrl = vm.thumbUrl(idKey),
                                        onClick = {
                                            sharedVm.put(idKey, video)
                                            onVideoClick(idKey)
                                        },
                                        onOpenInNicovideo = {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nicovideo.jp/watch/$idKey")))
                                            } catch (_: ActivityNotFoundException) {}
                                        },
                                        onOpenInNicobox = {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sp.nicovideo.jp/force-app-link/watch/$idKey")))
                                            } catch (_: ActivityNotFoundException) {}
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
