package com.gliffy.g2048.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import com.gliffy.g2048.game.Dir
import kotlin.math.abs

/**
 * 2048 board: a square gutter grid of cells plus one tile per board tile.
 * A single [Animatable] (0→1, 240 ms) restarts on every animated revision:
 *  - first half: board slides from the pre-move position (vanishers glide
 *    into their survivor and fade out, classic 2048 feel),
 *  - second half: the merged survivor pops and the freshly spawned tile
 *    scales in.
 * Positions are calculated in Dp from the board's measured size, so no
 * custom layout is required and each branch is only one line of math.
 */
@Composable
fun BoardPanel(
    snap: UiSnap,
    enabled: Boolean,
    isDark: Boolean,
    onMove: (Dir) -> Unit,
    onAnimSettled: () -> Unit,
) {
    val st = snap.state
    val ev = snap.moveEv
    val spawnEv = snap.spawnEv
    val n = st.size
    val density = LocalDensity.current

    var boardPx by remember { mutableIntStateOf(0) }
    var lastFxSeq by remember { mutableIntStateOf(-1) }
    val p = remember { Animatable(1f) }

    LaunchedEffect(snap.revision) {
        val fxSeq = (ev?.seq ?: spawnEv?.seq)?.toInt() ?: 0
        if (fxSeq != lastFxSeq && (ev != null || spawnEv != null)) {
            lastFxSeq = fxSeq
            p.snapTo(0f)
            p.animateTo(1f, animationSpec = tween(240, easing = FastOutSlowInEasing))
        }
        if (!snap.animSettled) onAnimSettled()
    }
    val v = p.value
    val slideT = (v * 2f).coerceIn(0f, 1f)
    val popT = ((v - 0.5f) * 2f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { boardPx = it.width }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val sx = down.position.x
                    val sy = down.position.y
                    val threshold = maxOf(24f, boardPx * 0.06f)
                    var fired = false
                    while (true) {
                        val ch = awaitPointerEvent().changes.first()
                        if (!ch.pressed) break
                        val dx = ch.position.x - sx
                        val dy = ch.position.y - sy
                        if (maxOf(abs(dx), abs(dy)) >= threshold) {
                            ch.consume()
                            fired = true
                            if (abs(dx) > abs(dy)) {
                                onMove(if (dx > 0) Dir.RIGHT else Dir.LEFT)
                            } else {
                                onMove(if (dy > 0) Dir.DOWN else Dir.UP)
                            }
                            break
                        }
                    }
                    if (!fired) return@awaitEachGesture
                }
            }
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (!enabled) return@onPreviewKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val d = when (ev.key) {
                    Key.DirectionUp -> Dir.UP
                    Key.DirectionDown -> Dir.DOWN
                    Key.DirectionLeft -> Dir.LEFT
                    Key.DirectionRight -> Dir.RIGHT
                    else -> return@onPreviewKeyEvent false
                }
                onMove(d)
                true
            }
    ) {
        if (boardPx <= 0) return@Box
        val b = with(density) { boardPx.toDp() }
        val pad = b * 0.025f
        val gut = b * 0.018f
        val cell = (b - pad * 2f - gut * (n - 1)) / n
        val boardShape = RoundedCornerShape(cell * 0.10f)
        val cellShape = RoundedCornerShape(cell * 0.07f)
        val cellGap = cell + gut

        fun xpos(c: Int): Dp = pad + cellGap * c
        fun ypos(r: Int): Dp = pad + cellGap * r

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isDark) Palette.boardDark else Palette.boardLight,
                    shape = boardShape,
                )
        ) {
            for (r in 0 until n) {
                for (c in 0 until n) {
                    Box(
                        modifier = Modifier
                            .offset(x = xpos(c), y = ypos(r))
                            .size(cell)
                            .background(
                                color = if (isDark) Palette.emptyDark else Palette.emptyLight,
                                shape = cellShape,
                            )
                    )
                }
            }

            val vanishSet = ev?.vanishing ?: emptySet()
            val anim = (ev != null || spawnEv != null) && v < 1f
            var idx = 0
            for (t in st.tiles) {
                if (t.id in vanishSet) { idx++; continue }
                val tx = xpos(t.col)
                val ty = ypos(t.row)
                var px = tx
                var py = ty
                if (anim && ev != null) {
                    val f = ev.from[t.id]
                    if (f != null) {
                        px = xpos(t.col) + (xpos(f.first.toInt()) - xpos(t.col)) * (1f - slideT)
                        py = ypos(t.row) + (ypos(f.second.toInt()) - ypos(t.row)) * (1f - slideT)
                    }
                }
                var scale = 1f
                if (anim) {
                    when {
                        ev != null && ev.mergedInto.values.contains(t.id) ->
                            scale = 1f + 0.15f * (1f - abs(2f * popT - 1f))
                        ev != null && ev.added?.id == t.id ->
                            scale = 0.2f + 0.8f * popT
                        spawnEv != null -> {
                            val local = (v * 1.3f - (idx % n) * 0.05f).coerceIn(0f, 1f)
                            scale = 0.2f + 0.8f * local
                        }
                    }
                }
                val (bg, fg) = Palette.tileOf(t.value)
                TileGlyph(
                    value = t.value,
                    x = px,
                    y = py,
                    cell = cell,
                    shape = cellShape,
                    scale = scale,
                    alpha = if (scale <= 0.02f) 0f else 1f,
                    bg = bg,
                    fg = fg,
                )
                idx++
            }

            // vanishing tiles ride on top: slide into the survivor and fade.
            if (anim && ev != null) {
                for (vid in ev.vanishing) {
                    val survId = ev.mergedInto[vid] ?: continue
                    val surv = st.tiles.firstOrNull { it.id == survId } ?: continue
                    val f = ev.from[vid]
                    val tx = xpos(surv.col)
                    val ty = ypos(surv.row)
                    val px = if (f != null) {
                        xpos(surv.col) + (xpos(f.first.toInt()) - xpos(surv.col)) * (1f - slideT)
                    } else tx
                    val py = if (f != null) {
                        ypos(surv.row) + (ypos(f.second.toInt()) - ypos(surv.row)) * (1f - slideT)
                    } else ty
                    val (bg, fg) = Palette.tileOf(surv.value / 2)
                    TileGlyph(
                        value = surv.value / 2,
                        x = px,
                        y = py,
                        cell = cell,
                        shape = cellShape,
                        scale = 1f - 0.95f * popT,
                        alpha = 1f - popT,
                        bg = bg,
                        fg = fg,
                    )
                }
            }
        }
    }
}

/** One 2048 tile, absolutely positioned within the board's Box. */
@Composable
internal fun TileGlyph(
    value: Int,
    x: Dp,
    y: Dp,
    cell: Dp,
    shape: RoundedCornerShape,
    scale: Float,
    alpha: Float,
    bg: Color,
    fg: Color,
) {
    // Auto-fit: scale the glyph so the number fills the tile's inner square
    // — as large as it can get while guaranteed to fit (height-cap for short
    // numbers, width-cap for long ones).
    val digits = value.toString().length
    val inner = cell.value * 0.84f
    val hFit = inner * 0.82f
    val wFit = inner / (digits * 0.58f)
    val fontSize = minOf(hFit, wFit).sp
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(cell)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = bg, shape = shape) {
            Text(
                text = value.toString(),
                modifier = Modifier.padding(cell * 0.05f),
                color = fg,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
