package com.nndd_re.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nndd_re.data.model.NnddVideo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: NnddVideo,
    thumbUrl: String,
    onClick: () -> Unit,
    onOpenInNicobox: (() -> Unit)? = null,
    onOpenInNicovideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val hasMenu = onOpenInNicobox != null || onOpenInNicovideo != null

    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (hasMenu) ({ showMenu = true }) else null
                ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.videoName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = video.videoName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatDuration(video.duration)}  再生${video.playCount.toInt()}回",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (hasMenu) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                onOpenInNicovideo?.let {
                    DropdownMenuItem(
                        text = { Text("ニコニコ動画で再生") },
                        onClick = { showMenu = false; it() }
                    )
                }
                onOpenInNicobox?.let {
                    DropdownMenuItem(
                        text = { Text("ニコボックスで再生") },
                        onClick = { showMenu = false; it() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: NnddVideo,
    thumbUrl: String,
    onClick: () -> Unit,
    onOpenInNicobox: (() -> Unit)? = null,
    onOpenInNicovideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val hasMenu = onOpenInNicobox != null || onOpenInNicovideo != null

    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (hasMenu) ({ showMenu = true }) else null
                ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.videoName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = video.videoName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatDuration(video.duration)}  再生${video.playCount.toInt()}回",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    video.folder.takeIf { it.isNotBlank() }?.let { folder ->
                        Text(
                            text = folder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (hasMenu) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                onOpenInNicovideo?.let {
                    DropdownMenuItem(
                        text = { Text("ニコニコ動画で再生") },
                        onClick = { showMenu = false; it() }
                    )
                }
                onOpenInNicobox?.let {
                    DropdownMenuItem(
                        text = { Text("ニコボックスで再生") },
                        onClick = { showMenu = false; it() }
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
