package com.nndd_re.ui.screen.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nndd_re.data.model.NnddComment
import kotlinx.coroutines.delay

/**
 * コメント一覧。再生位置の直前のコメントへ自動追従し、行タップでその位置へシークする。
 * [comments] は vposMs 昇順であること。
 */
@Composable
fun CommentListPanel(
    comments: List<NnddComment>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // 再生位置以前で最後のコメント (無ければ -1)
    // 二分探索なので毎回計算してよい。LaunchedEffect のキーは index なので、変わったときだけ動く
    val currentIndex = currentIndexAt(comments, currentPositionMs)
    // 触っている間と離して 3 秒間は追従しない
    var following by remember { mutableStateOf(true) }
    var touchCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentIndex, following) {
        if (following && currentIndex >= 0) listState.animateScrollToItem(currentIndex)
    }
    LaunchedEffect(touchCount) {
        if (touchCount > 0) {
            delay(3000)
            following = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(Unit) {
            // 子のスクロールを奪わないよう Initial パスで観測するだけにする
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                following = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                touchCount++
            }
        }
    ) {
        itemsIndexed(comments, key = { i, c -> "${c.no}_$i" }) { i, c ->
            val isCurrent = i == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        following = true
                        onSeek(c.vposMs)
                    }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = formatMs(c.vposMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = c.text.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun currentIndexAt(comments: List<NnddComment>, posMs: Long): Int {
    var lo = 0
    var hi = comments.size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (comments[mid].vposMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
    }
    return ans
}
