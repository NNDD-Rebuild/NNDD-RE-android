package com.nndd_re.ui.screen.player

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import com.nndd_re.util.VideoCache

private val POPUP_W = 120.dp
private val POPUP_H = 68.dp
private val POPUP_GAP = 6.dp

internal fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun PreviewVideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    key(player) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    tv.isOpaque = false
                    player.setVideoTextureView(tv)
                }
            },
            update = { tv -> player.setVideoTextureView(tv) },
            modifier = modifier
        )
    }
}

@Composable
internal fun SeekThumbnailPopup(
    seekFraction: Float,
    seekMs: Long,
    previewPlayer: ExoPlayer,
    fallbackThumbUrl: String,
    sliderCoords: LayoutCoordinates?,
    boxCoords: LayoutCoordinates?,
    boxMaxWidthPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val popupWpx = with(density) { POPUP_W.toPx() }
    val popupHpx = with(density) { POPUP_H.toPx() }
    val gapPx = with(density) { POPUP_GAP.toPx() }

    if (sliderCoords == null || boxCoords == null) return

    val sliderAbsX = sliderCoords.positionInRoot().x
    val boxAbsX = boxCoords.positionInRoot().x
    val sliderOffsetInBox = sliderAbsX - boxAbsX
    val sliderWidth = sliderCoords.size.width.toFloat()
    val thumbXInBox = sliderOffsetInBox + seekFraction * sliderWidth
    val rawOffsetX = thumbXInBox - popupWpx / 2f
    val clampedOffsetX = rawOffsetX.coerceIn(0f, (boxMaxWidthPx - popupWpx).coerceAtLeast(0f))

    val offsetXDp = with(density) { clampedOffsetX.toDp() }
    val offsetYDp = with(density) { -(popupHpx + gapPx).toDp() }

    Box(
        modifier = modifier
            .offset(x = offsetXDp, y = offsetYDp)
            .size(POPUP_W, POPUP_H)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // ロード中はカバー画像を表示
        AsyncImage(
            model = fallbackThumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // ExoPlayerがフレームを描画したら上書き（TextureView透過対応）
        PreviewVideoSurface(player = previewPlayer, modifier = Modifier.fillMaxSize())
        Text(
            text = formatMs(seekMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
internal fun SeekSliderRow(
    currentPositionMs: Long,
    duration: Long,
    isSeeking: Boolean,
    seekValue: Float,
    streamUrl: String,
    fallbackThumbUrl: String,
    onSeekStart: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var sliderCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // ポップアップ専用プレイヤー（メインと同じキャッシュ共有・ミュート・一時停止）
    val previewPlayer = remember(streamUrl) {
        val factory = CacheDataSource.Factory()
            .setCache(VideoCache.get(ctx))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build().also { p ->
                p.setMediaItem(MediaItem.fromUri(streamUrl))
                p.prepare()
                p.playWhenReady = false
                p.volume = 0f
            }
    }

    DisposableEffect(previewPlayer) {
        onDispose {
            previewPlayer.clearVideoSurface()
            previewPlayer.release()
        }
    }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { boxCoords = it }
    ) {
        val density = LocalDensity.current
        val boxMaxWidthPx = with(density) { maxWidth.toPx() }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeOverlay(currentPositionMs, duration)
            Slider(
                value = if (isSeeking) seekValue
                        else if (duration > 0) currentPositionMs.toFloat() / duration else 0f,
                onValueChange = { value ->
                    onSeekStart(value)
                    if (duration > 0) previewPlayer.seekTo((value * duration).toLong())
                },
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .onGloballyPositioned { sliderCoords = it },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            IconButton(onClick = onRotate) {
                Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White)
            }
        }

        if (isSeeking) {
            SeekThumbnailPopup(
                seekFraction = seekValue,
                seekMs = (seekValue * duration).toLong(),
                previewPlayer = previewPlayer,
                fallbackThumbUrl = fallbackThumbUrl,
                sliderCoords = sliderCoords,
                boxCoords = boxCoords,
                boxMaxWidthPx = boxMaxWidthPx,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
internal fun PlaybackCenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onRewind, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = "10秒戻る", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(60.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(30.dp))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        IconButton(onClick = onForward, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Default.Forward10, contentDescription = "10秒進む", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
internal fun TimeOverlay(
    currentPositionMs: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${formatMs(currentPositionMs)} / ${formatMs(duration)}",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun VideoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context -> TextureView(context).also { tv -> player.setVideoTextureView(tv) } },
        update = { tv -> player.setVideoTextureView(tv) },
        modifier = modifier
    )
}
