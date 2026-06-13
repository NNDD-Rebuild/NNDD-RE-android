package com.nndd_re.ui.screen.player

import android.graphics.Paint
import android.graphics.Typeface
import com.nndd_re.data.model.NnddComment
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// niconicomments 0.2.78 互換レイアウトエンジン (html5 モード)
// 内部座標系: 1920 x 1080

internal object NicoConfig {
    const val CANVAS_W = 1920f
    const val CANVAS_H = 1080f
    const val DRAW_RANGE = 1530f
    const val DRAW_PADDING = 195f
    const val SPEED_OFFSET = 0.95f
    const val COLL_RIGHT = 1685f
    const val COLL_LEFT = 235f
    const val COLL_PADDING = 5f

    // commentStageSize.html5: fullWidth=683, height=384
    // commentScale = 1920 / 683
    private const val STAGE_H = 384.0
    private const val SCALE = CANVAS_W / 683f

    // getCharSize: stageH / doubleResized[size]
    // getFontSizeAndScale: charSize * 0.8 -> floor  (mapping to actual pixel font)
    // 内部フォントサイズ = floor(stageH / doubleResized * 0.8) * SCALE
    val FONT_MEDIUM = floor(STAGE_H / 11.3 * 0.8).toFloat() * SCALE  // ≈ 75.9
    val FONT_BIG    = floor(STAGE_H / 7.8  * 0.8).toFloat() * SCALE  // ≈ 109.7
    val FONT_SMALL  = floor(STAGE_H / 16.6 * 0.8).toFloat() * SCALE  // ≈ 50.6

    // デフォルト表示長 (vpos 単位, 1vpos = 10ms)
    const val DEFAULT_LONG = 300
}

// \n を含むコメントを AA として扱う
internal fun isAA(text: String) = '\n' in text

// Unicode East Asian Width: Wide/Fullwidth 判定
private fun isFullWidth(ch: Char): Boolean {
    val c = ch.code
    return c >= 0x1100 && (
        c <= 0x115F || c == 0x2329 || c == 0x232A ||
        c in 0x2E80..0x303E || c in 0x3040..0xA4CF ||
        c in 0xA960..0xA97F || c in 0xAC00..0xD7AF ||
        c in 0xF900..0xFAFF || c in 0xFE10..0xFE19 ||
        c in 0xFE30..0xFE6F || c in 0xFF00..0xFF60 ||
        c in 0xFFE0..0xFFE6
    )
}

// AA行の幅: 全角=fontSize, 半角=fontSize×0.5
internal fun measureAALine(line: String, fontSize: Float): Float =
    line.fold(0f) { acc, ch -> acc + if (isFullWidth(ch)) fontSize else fontSize * 0.5f }

// class にして参照同一性 (===) で衝突リスト管理
internal class NicoItem(
    val index: Int,
    val vpos: Int,
    val long: Int,
    val width: Float,
    val height: Float,
    val loc: String,
    val owner: Boolean,
    val layer: Int,
    var posY: Float,
    val source: NnddComment,
    val fontSize: Float,
    val isAA: Boolean = false
)

private typealias CollMap = HashMap<Int, MutableList<NicoItem>>

private class NicoCollision {
    val right = CollMap()
    val left  = CollMap()
}

// ───────────────────── ピュアロジック ─────────────────────

private data class PosYResult(val pos: Float, val changed: Boolean, val doBreak: Boolean)

private fun getPosY(cur: Float, target: NicoItem, coll: List<NicoItem>?): PosYResult {
    if (coll == null) return PosYResult(cur, false, false)
    var pos = cur
    var changed = false
    val h = target.height
    outer@ while (true) {
        for (item in coll) {
            if (item === target || item.posY < 0) continue
            if (item.owner == target.owner && item.layer == target.layer &&
                pos < item.posY + item.height && pos + h > item.posY
            ) {
                pos = item.posY + item.height
                changed = true
                if (pos + h > NicoConfig.CANVAS_H) {
                    pos = if (NicoConfig.CANVAS_H < h) {
                        if (target.loc == "naka") (h - NicoConfig.CANVAS_H) / -2f else 0f
                    } else {
                        (Math.random() * (NicoConfig.CANVAS_H - h)).toFloat()
                    }
                    return PosYResult(pos, true, true)
                }
                continue@outer
            }
        }
        break
    }
    return PosYResult(pos, changed, false)
}

private fun getFixedPosY(comment: NicoItem, coll: CollMap): Float {
    var posY = 0f
    var changed = true
    var count = 0
    while (changed && count < 10) {
        changed = false
        count++
        for (j in 0 until comment.long) {
            val r = getPosY(posY, comment, coll[comment.vpos + j])
            posY = r.pos
            changed = changed || r.changed
            if (r.doBreak) break
        }
    }
    return posY
}

private fun nakaSpeed(item: NicoItem) =
    (NicoConfig.DRAW_RANGE + item.width * NicoConfig.SPEED_OFFSET) / (item.long + 100)

private fun getMovablePosY(item: NicoItem, coll: NicoCollision, beforeVpos: Int, speed: Float): Float {
    if (NicoConfig.CANVAS_H < item.height) return (item.height - NicoConfig.CANVAS_H) / -2f
    val n = item.long + 125
    var posY = 0f
    var changed = true
    var count = 0
    var lastUpdated: Int? = null
    outer@ while (changed && count < 10) {
        changed = false
        count++
        var j = beforeVpos
        while (j < n) {
            val vpos = item.vpos + j
            val lp = NicoConfig.DRAW_PADDING + NicoConfig.DRAW_RANGE - (j + 100) * speed
            if (lastUpdated != null && lastUpdated == vpos) return posY
            var doBreak = false
            if (lp + item.width >= NicoConfig.COLL_RIGHT && lp <= NicoConfig.COLL_RIGHT) {
                val r = getPosY(posY, item, coll.right[vpos])
                posY = r.pos; changed = changed || r.changed
                if (r.changed) lastUpdated = vpos
                doBreak = doBreak || r.doBreak
            }
            if (lp + item.width >= NicoConfig.COLL_LEFT && lp <= NicoConfig.COLL_LEFT) {
                val r = getPosY(posY, item, coll.left[vpos])
                posY = r.pos; changed = changed || r.changed
                if (r.changed) lastUpdated = vpos
                doBreak = doBreak || r.doBreak
            }
            if (doBreak) return posY
            j += 5
        }
    }
    return posY
}

private fun addTo(map: CollMap, vpos: Int, item: NicoItem) {
    val list = map.getOrPut(vpos) { mutableListOf() }
    if (list.none { it === item }) list.add(item)
}

private fun processFixed(item: NicoItem, coll: CollMap, timeline: CollMap) {
    val collEnd = max(item.long - 20, 0)
    item.posY = getFixedPosY(item, coll)
    for (j in 0 until item.long) {
        addTo(timeline, item.vpos + j, item)
        if (j <= collEnd) addTo(coll, item.vpos + j, item)
    }
}

private fun processMovable(item: NicoItem, coll: NicoCollision, timeline: CollMap) {
    val speed = nakaSpeed(item)
    val beforeVpos = (-288f / ((1632f + item.width) / (item.long + 125))).roundToInt() - 100
    item.posY = getMovablePosY(item, coll, beforeVpos, speed)
    val n = item.long + 125
    for (j in beforeVpos until n) {
        val vpos = item.vpos + j
        val lp = NicoConfig.DRAW_PADDING + NicoConfig.DRAW_RANGE - (j + 100) * speed
        addTo(timeline, vpos, item)
        if (lp + item.width + NicoConfig.COLL_PADDING >= NicoConfig.COLL_RIGHT && lp <= NicoConfig.COLL_RIGHT)
            addTo(coll.right, vpos, item)
        if (lp + item.width + NicoConfig.COLL_PADDING >= NicoConfig.COLL_LEFT && lp <= NicoConfig.COLL_LEFT)
            addTo(coll.left, vpos, item)
    }
}

// ───────────────────── 公開 API ─────────────────────

internal fun getPosX(item: NicoItem, vposCurrent: Int): Float {
    if (item.loc != "naka") return (NicoConfig.CANVAS_W - item.width) / 2f
    val speed = nakaSpeed(item)
    return NicoConfig.DRAW_PADDING + NicoConfig.DRAW_RANGE - (vposCurrent - item.vpos + 100) * speed
}

/**
 * niconicomments 互換の位置計算。
 * @param screenW  実画面幅 (px)
 * @param mediumPx 実画面での medium フォントサイズ (px) — 旧実装方式 36f * screenH / 683f
 */
internal fun buildNicoLayout(
    comments: List<NnddComment>,
    typeface: Typeface,
    screenW: Float,
    mediumPx: Float
): List<NicoItem> {
    // 実画面フォントサイズ → 内部座標(1920px)フォントサイズへ変換
    val scale = screenW / NicoConfig.CANVAS_W
    val fontMedium = mediumPx / scale
    val fontBig    = fontMedium * 1.4f
    val fontSmall  = fontMedium * 0.7f

    val items = comments.sortedBy { it.vposMs }.mapIndexed { idx, c ->
        val fontSize = when (c.size) {
            0    -> fontBig
            2    -> fontSmall
            else -> fontMedium
        }
        val paint = Paint().apply { textSize = fontSize; this.typeface = typeface; isAntiAlias = true }
        val aa = isAA(c.text)
        val lines = if (aa) c.text.trimEnd('\n').split('\n') else listOf(c.text)
        var effectiveFontSize = fontSize
        if (aa) {
            // 文字幅カウントで rawMaxW を計算 (全角=fontSize, 半角=×0.5)
            val rawMaxW = lines.maxOf { measureAALine(it, fontSize) }
            val rawH = fontSize * lines.size
            val scaleH = if (rawH > NicoConfig.CANVAS_H) NicoConfig.CANVAS_H / rawH else 1f
            val scaleW = if (rawMaxW > NicoConfig.CANVAS_W) NicoConfig.CANVAS_W / rawMaxW else 1f
            effectiveFontSize = fontSize * minOf(scaleH, scaleW)
        }
        val textW = if (aa) lines.maxOf { measureAALine(it, effectiveFontSize) }
                    else paint.measureText(lines[0])
        val totalH = effectiveFontSize * lines.size
        NicoItem(
            index = idx,
            vpos  = (c.vposMs / 10).toInt(),
            long  = NicoConfig.DEFAULT_LONG,
            width = textW,
            height = totalH,
            loc   = c.pos,
            owner = false,
            layer = if (aa && c.pos == "naka") 1 else 0,
            posY  = 0f,
            source = c,
            fontSize = effectiveFontSize,
            isAA = aa
        )
    }

    val ueColl    = CollMap()
    val shitaColl = CollMap()
    val nakaColl  = NicoCollision()
    val timeline  = CollMap()

    for (item in items) {
        when (item.loc) {
            "ue"    -> processFixed(item, ueColl, timeline)
            "shita" -> processFixed(item, shitaColl, timeline)
            else    -> processMovable(item, nakaColl, timeline)
        }
    }
    return items
}
