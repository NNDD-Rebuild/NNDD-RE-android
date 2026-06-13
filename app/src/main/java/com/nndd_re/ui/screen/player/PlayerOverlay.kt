package com.nndd_re.ui.screen.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.text.Html
import android.util.Log
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nndd_re.MainActivity
import com.nndd_re.util.VideoCache
import com.nndd_re.data.repository.NnddRepository
import com.nndd_re.ui.PlayerUiState
import com.nndd_re.ui.SharedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

@Composable
fun PlayerOverlay(
    repo: NnddRepository,
    videoId: String,
    sharedVm: SharedViewModel,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val mainActivity = activity as? MainActivity
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val vm: PlayerViewModel = viewModel(
        key = videoId,
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlayerViewModel(repo, videoId, sharedVm) as T
            }
        }
    )
    val comments by vm.comments.collectAsState()
    val video by vm.video.collectAsState()

    val commentFontPath by com.nndd_re.util.ServerPreferences.commentFontPathFlow(ctx).collectAsState(initial = "")
    val commentTypeface = remember(commentFontPath) {
        if (commentFontPath.isNotBlank())
            runCatching { android.graphics.Typeface.createFromFile(commentFontPath) }
                .getOrDefault(android.graphics.Typeface.MONOSPACE)
        else android.graphics.Typeface.MONOSPACE
    }
    val commentState = rememberCommentOverlayState()
    val skipDurationSec by com.nndd_re.util.ServerPreferences.skipDurationFlow(ctx).collectAsState(initial = 10)
    val skipDurationMs = skipDurationSec * 1000L

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showComments by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // コントロール自動非表示（シーク中は消さない）
    var controlsResetKey by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(controlsResetKey, isSeeking) {
        showControls = true
        if (!isSeeking) {
            delay(3000)
            showControls = false
        }
    }

    // 横画面用コントロール自動非表示（シーク中は消さない）
    var lsUiKey by remember { mutableIntStateOf(0) }
    var showLsUi by remember { mutableStateOf(true) }
    LaunchedEffect(lsUiKey, isSeeking) {
        showLsUi = true
        if (!isSeeking) {
            delay(3000)
            showLsUi = false
        }
    }

    val cacheDataSourceFactory = remember {
        CacheDataSource.Factory()
            .setCache(VideoCache.get(ctx))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build().also { p ->
                p.setMediaItem(MediaItem.fromUri(vm.streamUrl))
                p.prepare()
                p.playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0)
                    videoAspectRatio = size.width.toFloat() / size.height * size.pixelWidthHeightRatio
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            currentPositionMs = player.currentPosition
            val d = player.duration
            if (d > 0) duration = d
            delay(16)
        }
    }

    DisposableEffect(Unit) {
        mainActivity?.isPlayerActive = true
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            mainActivity?.isPlayerActive = false
            player.clearVideoSurface()
            player.release()
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let {
                WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(isLandscape) {
        val w = activity?.window ?: return@LaunchedEffect
        val ctrl = WindowInsetsControllerCompat(w, w.decorView)
        if (isLandscape) {
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (sharedVm.playerUiState == PlayerUiState.MINI) {
                sharedVm.playerUiState = PlayerUiState.FULL
            }
        } else {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 0f = FULL, 1f = MINI
    val animProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sharedVm.playerUiState) {
        when (sharedVm.playerUiState) {
            PlayerUiState.FULL -> animProgress.animateTo(0f, spring(stiffness = 380f, dampingRatio = 0.82f))
            PlayerUiState.MINI -> animProgress.animateTo(1f, spring(stiffness = 380f, dampingRatio = 0.82f))
            PlayerUiState.HIDDEN -> {}
        }
    }

    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            sharedVm.playerUiState = PlayerUiState.HIDDEN
            onClose()
        }
    }

    if (isLandscape) {
        // ─── 横画面: 全画面プレイヤー ───
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(skipDurationMs) {
                    detectTapGestures(
                        onTap = { if (showLsUi) showLsUi = false else lsUiKey++ },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2)
                                player.seekTo((player.currentPosition - skipDurationMs).coerceAtLeast(0L))
                            else
                                player.seekTo((player.currentPosition + skipDurationMs).coerceAtMost(duration))
                            lsUiKey++
                        }
                    )
                }
        ) {
            val sr = maxWidth.value / maxHeight.value
            val vMod = if (videoAspectRatio >= sr)
                Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            else
                Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
            Box(modifier = vMod.align(Alignment.Center)) {
                VideoSurface(player = player, modifier = Modifier.matchParentSize())
            }
            if (showComments) {
                CommentOverlay(
                    comments = comments,
                    currentPositionMs = currentPositionMs,
                    typeface = commentTypeface,
                    state = commentState,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AnimatedVisibility(visible = showLsUi, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED },
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                    IconButton(
                        onClick = { showComments = !showComments },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Icon(
                            if (showComments) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (showComments) Color.White else Color.White.copy(alpha = 0.4f)
                        )
                    }
                    PlaybackCenterControls(
                        isPlaying = isPlaying,
                        onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                        onRewind = { player.seekTo((player.currentPosition - skipDurationMs).coerceAtLeast(0L)) },
                        onForward = { player.seekTo((player.currentPosition + skipDurationMs).coerceAtMost(duration)) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        SeekSliderRow(
                            currentPositionMs = currentPositionMs,
                            duration = duration,
                            isSeeking = isSeeking,
                            seekValue = seekValue,
                            streamUrl = vm.streamUrl,
                            fallbackThumbUrl = vm.thumbUrl,
                            onSeekStart = { isSeeking = true; seekValue = it },
                            onSeekFinished = { player.seekTo((seekValue * duration).toLong()); isSeeking = false },
                            onRotate = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        return
    }

    // ─── 縦画面: YouTube風ミニプレイヤー ───
    BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val miniW = with(density) { 164.dp.toPx() }
        val miniH = miniW / (16f / 9f)
        val edgePx = with(density) { 12.dp.toPx() }
        val botPx = with(density) { 80.dp.toPx() }

        val p = animProgress.value

        // 動画コンテナの寸法・位置
        val videoW = lerpF(screenW, miniW, p)
        val videoH = videoW / (16f / 9f)
        val videoX = lerpF(0f, screenW - miniW - edgePx, p)
        val videoY = lerpF(0f, screenH - miniH - botPx, p)
        val cornerDp = lerpF(0f, 12f, p)
        val elevDp = lerpF(0f, 10f, p)

        // 説明欄の押し出し量・透明度
        val descPush = lerpF(0f, screenH * 0.45f, p)
        val descAlpha = (1f - p * 2.8f).coerceIn(0f, 1f)

        // ─── 背景 ───
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        // ─── ミニプレイヤー バー背景 ───
        if (p > 0.4f) {
            val barAlpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { miniH.toDp() })
                    .offset { IntOffset(0, videoY.toInt()) }
                    .shadow(8.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(barAlpha)
            )
        }

        // ─── 動画コンテナ ───
        Box(
            modifier = Modifier
                .offset { IntOffset(videoX.toInt(), videoY.toInt()) }
                .size(with(density) { videoW.toDp() }, with(density) { videoH.toDp() })
                .shadow(elevDp.dp, RoundedCornerShape(cornerDp.dp))
                .clip(RoundedCornerShape(cornerDp.dp))
                .background(Color.Black)
        ) {
            VideoSurface(player = player, modifier = Modifier.matchParentSize())

            if (p < 0.35f && showComments) {
                CommentOverlay(
                    comments = comments,
                    currentPositionMs = currentPositionMs,
                    typeface = commentTypeface,
                    state = commentState,
                    modifier = Modifier.matchParentSize()
                )
            }

            // FULL コントロール
            if (p < 0.5f) {
                val ctrlAlpha = (1f - p * 2f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(skipDurationMs, showControls) {
                            detectTapGestures(
                                onTap = { if (showControls) showControls = false else controlsResetKey++ },
                                onDoubleTap = { offset ->
                                    if (offset.x < size.width / 2)
                                        player.seekTo((player.currentPosition - skipDurationMs).coerceAtLeast(0L))
                                    else
                                        player.seekTo((player.currentPosition + skipDurationMs).coerceAtMost(duration))
                                    controlsResetKey++
                                }
                            )
                        }
                ) {
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize().alpha(ctrlAlpha)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            IconButton(
                                onClick = { sharedVm.playerUiState = PlayerUiState.HIDDEN; onClose() },
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                            }
                            IconButton(
                                onClick = { showComments = !showComments },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    if (showComments) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (showComments) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                            }
                            PlaybackCenterControls(
                                isPlaying = isPlaying,
                                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                                onRewind = { player.seekTo((player.currentPosition - skipDurationMs).coerceAtLeast(0L)) },
                                onForward = { player.seekTo((player.currentPosition + skipDurationMs).coerceAtMost(duration)) },
                                modifier = Modifier.align(Alignment.Center)
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                SeekSliderRow(
                                    currentPositionMs = currentPositionMs,
                                    duration = duration,
                                    isSeeking = isSeeking,
                                    seekValue = seekValue,
                                    streamUrl = vm.streamUrl,
                                    fallbackThumbUrl = vm.thumbUrl,
                                    onSeekStart = { isSeeking = true; seekValue = it },
                                    onSeekFinished = { player.seekTo((seekValue * duration).toLong()); isSeeking = false },
                                    onRotate = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── ミニプレイヤー: タイトル + ボタン ───
        if (p > 0.4f) {
            val miniBarAlpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val titleX = videoX + videoW + with(density) { 8.dp.toPx() }
            val btnW = with(density) { 88.dp.toPx() }
            val titleW = (screenW - titleX - btnW - edgePx).coerceAtLeast(0f)

            // タイトル
            Box(
                modifier = Modifier
                    .offset { IntOffset(titleX.toInt(), videoY.toInt()) }
                    .size(with(density) { titleW.toDp() }, with(density) { miniH.toDp() })
                    .alpha(miniBarAlpha),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = video?.videoName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 再生/一時停止 + 閉じる
            Row(
                modifier = Modifier
                    .offset { IntOffset((screenW - btnW - edgePx).toInt(), videoY.toInt()) }
                    .size(with(density) { btnW.toDp() }, with(density) { miniH.toDp() })
                    .alpha(miniBarAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                }
                IconButton(onClick = { sharedVm.playerUiState = PlayerUiState.HIDDEN; onClose() }) {
                    Icon(Icons.Default.Close, contentDescription = "閉じる")
                }
            }
        }

        // ─── 説明欄 ───
        if (descAlpha > 0f) {
            val descTopY = videoY + videoH + with(density) { 8.dp.toPx() } + descPush
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, descTopY.toInt()) }
                    .heightIn(max = with(density) { (screenH - descTopY).toDp() })
                    .alpha(descAlpha)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                video?.let { v ->
                    Text(
                        text = v.videoName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    val dur = v.duration.toInt().let { s -> "%d:%02d".format(s / 60, s % 60) }
                    val date = (v.pubDate ?: v.creationDate)?.take(10) ?: ""
                    Text(
                        text = buildString {
                            append(dur)
                            v.viewCount?.let { append("  再生${it}回") }
                                ?: append("  再生${v.playCount.toInt()}回")
                            v.commentCount?.let { append("  コメ${it}") }
                            v.mylistCount?.let { append("  マイリス${it}") }
                            if (date.isNotEmpty()) append("  $date")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    v.ownerNickname?.let {
                        Spacer(Modifier.height(2.dp))
                        Text("投稿者: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (v.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(v.tags) { tag -> SuggestionChip(onClick = {}, label = { Text(tag) }) }
                        }
                    }
                    v.description?.takeIf { it.isNotBlank() }?.let { html ->
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        val textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
                        val colorOnBg = MaterialTheme.colorScheme.onBackground.toArgb()
                        val colorPrimary = MaterialTheme.colorScheme.primary.toArgb()
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx ->
                                android.widget.TextView(ctx).apply {
                                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                    highlightColor = android.graphics.Color.TRANSPARENT
                                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                                }
                            },
                            update = { tv ->
                                tv.setTextColor(colorOnBg)
                                tv.setLinkTextColor(colorPrimary)
                                val zwsUrl: String.() -> String = {
                                    replace("/", "/​")
                                        .replace(".", ".​")
                                        .replace("-", "-​")
                                        .replace("_", "_​")
                                }
                                val isHtml = html.contains(Regex("""<(br|p|a)\b""", RegexOption.IGNORE_CASE))
                                val processedHtml = if (isHtml) {
                                    // HTML形式: <a> タグ内 URL に U+200B 挿入
                                    html.replace(Regex("""(<a\b[^>]*>)(https?://[^<]+)(</a>)""")) { mr ->
                                        "${mr.groupValues[1]}${mr.groupValues[2].zwsUrl()}${mr.groupValues[3]}"
                                    }
                                } else {
                                    // プレーンテキスト形式（旧NNDD）: bare URL を <br><a> でラップ
                                    html.replace(Regex("""(https?://\S+)""")) { mr ->
                                        "<br><a href=\"${mr.value}\">${mr.value.zwsUrl()}</a><br>"
                                    }
                                }
                                // sm/nm/mylist を既存<a>タグ外でリンク化
                                val finalHtml = processedHtml.replace(
                                    Regex("""(<a\b[^>]*>[\s\S]*?</a>)|((?:sm|nm)\d+|mylist/\d+)""")
                                ) { m ->
                                    if (m.groupValues[1].isNotEmpty()) {
                                        m.value
                                    } else {
                                        val v = m.groupValues[2]
                                        val url = if (v.startsWith("mylist/"))
                                            "https://www.nicovideo.jp/$v"
                                        else
                                            "https://www.nicovideo.jp/watch/$v"
                                        "<a href=\"$url\">$v</a>"
                                    }
                                }
                                @Suppress("DEPRECATION")
                                val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                    Html.fromHtml(finalHtml, Html.FROM_HTML_MODE_LEGACY)
                                else Html.fromHtml(finalHtml)
                                tv.text = spanned
                            }
                        )
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}
