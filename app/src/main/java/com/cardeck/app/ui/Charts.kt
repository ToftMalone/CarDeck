package com.cardeck.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardeck.app.data.fr

/** Spline de Catmull-Rom passant par [points], en courbes de Bézier. */
fun smoothPath(points: List<Offset>, path: Path = Path()): Path {
    path.reset()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

/** Chaque point glisse de sa valeur précédente à la nouvelle en [durationMs] (la courbe se transforme, elle ne saute pas). */
@Composable
fun animatedSamples(samples: List<Float>, durationMs: Int): List<Float> {
    var from by remember { mutableStateOf(samples) }
    var to by remember { mutableStateOf(samples) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(samples) {
        val p = progress.value
        from = if (from.size == to.size) from.mapIndexed { i, v -> v + (to[i] - v) * p } else samples
        to = samples
        if (from.size != to.size) from = to
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    val p = progress.value
    return if (from.size != to.size) to else to.mapIndexed { i, v -> from[i] + (v - from[i]) * p }
}

/**
 * Courbe en direct (fenêtre glissante) : grille en pointillés avec ses graduations, courbe lissée, aire teintée
 * et point de la dernière valeur. [samples] remplissent [capacity] places, de gauche à droite.
 */
@Composable
fun LiveChart(
    samples: List<Float>,
    min: Float,
    max: Float,
    gridStep: Float,
    color: Color,
    modifier: Modifier = Modifier,
    capacity: Int = samples.size,
    decimals: Int = 0,
    morphMs: Int = 500,
    height: Dp = 148.dp,
) {
    val c = Cd.c
    val values = animatedSamples(samples, morphMs)
    val gridValues = generateSequence(max) { it - gridStep }.takeWhile { it >= min - 0.001f }.toList()
    val dotBorder = c.sf1
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.matchParentSize()) {
            val left = 40.dp.toPx()
            val top = 8.dp.toPx()
            val h = size.height - 28.dp.toPx()
            val w = size.width - left
            fun y(v: Float) = top + h - (v.coerceIn(min, max) - min) / (max - min) * h
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            gridValues.forEach { g -> drawLine(c.olv, Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx(), pathEffect = dash) }
            if (values.size < 2) return@Canvas
            val n = maxOf(capacity, values.size)
            val step = w / (n - 1)
            val startX = left + (n - values.size) * step
            val pts = values.mapIndexed { i, v -> Offset(startX + i * step, y(v)) }
            val line = smoothPath(pts)
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, top + h); lineTo(pts.first().x, top + h); close()
            }
            drawPath(fill, color.copy(alpha = 0.14f))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(dotBorder, 6.5f.dp.toPx(), pts.last())
            drawCircle(color, 4.25f.dp.toPx(), pts.last())
        }
        gridValues.forEach { g ->
            val frac = (max - g) / (max - min)
            Text(fr(g, decimals), Modifier.offset(y = 8.dp + (height - 28.dp) * frac - 7.dp), style = rf(10, 14), color = c.onv)
        }
    }
}

/** Pas de graduation « rond » donnant environ 3 intervalles entre [min] et [max]. */
fun niceStep(min: Double, max: Double): Double {
    val raw = (max - min) / 3
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
}

/**
 * Jauge en demi-cercle (180°, trait de 16 dp), valeur poussée par le ressort de base ; grande valeur au centre et les
 * bornes sous chaque extrémité.
 */
@Composable
fun SemiGauge(
    fraction: Float?,
    color: Color,
    track: Color,
    display: String,
    label: String,
    start: String,
    end: String,
    modifier: Modifier = Modifier,
    labelColor: Color = Cd.c.on,
) {
    val frac by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), Motion.standard(), label = "semi")
    BoxWithConstraints(modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(300f / 184f)) {
        val u = maxWidth / 300f
        Canvas(Modifier.matchParentSize()) {
            val k = size.width / 300f
            val stroke = Stroke(16.dp.toPx(), cap = StrokeCap.Round)
            val r = 124 * k
            val topLeft = Offset(150 * k - r, 150 * k - r)
            drawArc(track, 180f, 180f, false, topLeft, Size(r * 2, r * 2), style = stroke)
            if (frac > 0.001f) drawArc(color, 180f, 180f * frac, false, topLeft, Size(r * 2, r * 2), style = stroke)
        }
        Column(Modifier.fillMaxWidth().padding(top = u * 70), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(display, style = gs(52, 56, 500, -1.5f, tnum = true), color = labelColor, maxLines = 1)
            Text(label, Modifier.padding(top = 2.dp), style = rf(13, 18, 500), color = labelColor, maxLines = 1)
        }
        val endColor = labelColor.copy(alpha = 0.75f)
        Text(start, Modifier.width(u * 52).offset(x = u * 0, y = u * 164), style = rf(11, 14), color = endColor, textAlign = TextAlign.Center)
        Text(end, Modifier.width(u * 52).offset(x = u * 248, y = u * 164), style = rf(11, 14), color = endColor, textAlign = TextAlign.Center)
    }
}

/** Petit anneau (trait de 7 dp) rempli selon [fraction], poussé par le ressort de base. */
@Composable
fun QualityRing(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 56.dp, track: Color = Cd.c.sf4, stroke: Dp = 7.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0.02f, 1f), Motion.standard(), label = "ring")
    val col by animateColorAsState(color, tween(300), label = "ringColor")
    Canvas(modifier.size(size)) {
        val sw = stroke.toPx() * (size.toPx() / 56.dp.toPx())
        val r = this.size.minDimension * 22f / 56f
        val tl = Offset(center.x - r, center.y - r)
        drawCircle(track, r, style = Stroke(sw))
        drawArc(col, -90f, 360f * f, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
    }
}
