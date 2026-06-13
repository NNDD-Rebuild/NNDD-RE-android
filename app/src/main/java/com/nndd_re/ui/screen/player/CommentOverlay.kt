package com.nndd_re.ui.screen.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.nndd_re.data.model.NnddComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STROKE_ALPHA = 38

// isLandscape の分岐をまたいで items を保持するためのホルダー
// PlayerScreen 側で rememberCommentOverlayState() で生成して両方に渡す
class CommentOverlayState {
    internal var items by mutableStateOf(emptyList<NicoItem>())
}

@Composable
fun rememberCommentOverlayState() = remember { CommentOverlayState() }

@Composable
fun CommentOverlay(
    comments: List<NnddComment>,
    currentPositionMs: Long,
    typeface: Typeface = Typeface.MONOSPACE,
    state: CommentOverlayState = rememberCommentOverlayState(),
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val scale = screenW / NicoConfig.CANVAS_W

        val mediumPx = (36f * screenH / 683f).coerceIn(12f, 120f)

        val layoutKey = Triple(typeface, screenW.toInt(), mediumPx.toInt())
        LaunchedEffect(comments, layoutKey) {
            val result = withContext(Dispatchers.Default) {
                buildNicoLayout(comments, typeface, screenW, mediumPx)
            }
            state.items = result
        }

        val items = state.items

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val vposCurrent = (currentPositionMs / 10).toInt()
                val longMs = NicoConfig.DEFAULT_LONG * 10L

                for (item in items) {
                    val elapsed = currentPositionMs - item.source.vposMs

                    val x = getPosX(item, vposCurrent) * scale
                    val scaledW = item.width * scale
                    val scaledFont = item.fontSize * scale

                    when (item.loc) {
                        "naka" -> if (x > screenW || x + scaledW < 0f) continue
                        else   -> if (elapsed < 0L || elapsed > longMs) continue
                    }

                    val baselineY = when (item.loc) {
                        "shita" -> (NicoConfig.CANVAS_H - item.posY) * scale
                        else    -> (item.posY + item.height) * scale
                    }
                    if (baselineY < 0f || baselineY - scaledFont > screenH) continue

                    val fillColor = (0xFF000000L or (item.source.color and 0xFFFFFFL)).toInt()
                    val renderTypeface = if (item.isAA) Typeface.DEFAULT else typeface
                    val fillPaint = Paint().apply {
                        textSize = scaledFont
                        color = fillColor
                        isAntiAlias = true
                        this.typeface = renderTypeface
                    }
                    val sc = item.source.strokeColor ?: 0L
                    val strokePaint = Paint(fillPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = scaledFont * 0.12f
                        color = android.graphics.Color.argb(
                            STROKE_ALPHA,
                            ((sc shr 16) and 0xFF).toInt(),
                            ((sc shr 8)  and 0xFF).toInt(),
                            (sc          and 0xFF).toInt()
                        )
                    }

                    if (item.isAA) {
                        val lines = item.source.text.trimEnd('\n').split('\n')
                        lines.forEachIndexed { i, line ->
                            val ly = when (item.loc) {
                                "shita" -> (NicoConfig.CANVAS_H - item.posY - (lines.size - 1 - i) * item.fontSize) * scale
                                else    -> (item.posY + (i + 1) * item.fontSize) * scale
                            }
                            native.drawText(line, x, ly, strokePaint)
                            native.drawText(line, x, ly, fillPaint)
                        }
                    } else {
                        native.drawText(item.source.text, x, baselineY, strokePaint)
                        native.drawText(item.source.text, x, baselineY, fillPaint)
                    }
                }
            }
        }
    }
}
