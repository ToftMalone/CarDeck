package com.cardeck.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import java.util.Locale

// =============================================================================================
// Formatage des nombres

/** Formate un nombre avec [decimals] décimales dans la langue courante ; « — » si absent. */
fun fmt(v: Double?, decimals: Int = 0): String =
    if (v == null || v.isNaN()) "—" else String.format(Locale.getDefault(), "%.${decimals}f", v)

/** Texte d'une valeur en direct : animée au ressort, en chiffres tabulaires. */
@Composable
fun LiveNumber(value: Double?, decimals: Int, style: TextStyle, color: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    val v = animatedNumber(value)
    Text(fmt(v, decimals), modifier, color = color, style = style.copy(fontFeatureSettings = TNUM), maxLines = 1)
}

// =============================================================================================
// Badges « cookie »

/** Badge d'illustration en forme de cookie (M3 Expressive), en rotation très lente ; l'icône reste droite. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookieBadge(
    sym: Sym,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = Accent.current.container,
    content: Color = Accent.current.onContainer,
    filled: Boolean = true,
    rotating: Boolean = true,
    polygon: RoundedPolygon = if (size >= 80.dp) MaterialShapes.Cookie12Sided else MaterialShapes.Cookie9Sided,
    periodMs: Int = if (size >= 80.dp) 40_000 else 32_000,
) {
    val shape = polygon.toShape()
    val angle = if (rotating) slowRotation(periodMs).value else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }.clip(shape).background(container))
        Symbol(sym, size = size * 0.46f, tint = content, filled = filled)
    }
}

// =============================================================================================
// Indicateurs animés

/** Anneaux qui s'élargissent : activité en cours. */
@Composable
fun PulseRing(color: Color, modifier: Modifier = Modifier, rings: Int = 3, periodMs: Int = 2400) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val waves = (0 until rings).map { i ->
        inf.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(periodMs, easing = Motion.Emphasized), initialStartOffset = StartOffset(i * periodMs / rings)),
            label = "wave$i",
        )
    }
    Canvas(modifier) {
        val r = size.minDimension / 2
        waves.forEach { w ->
            val v = w.value
            drawCircle(color.copy(alpha = (0.55f * (1 - v)).coerceIn(0f, 1f)), radius = r * (0.45f + 0.55f * v), style = Stroke(2.dp.toPx()))
        }
    }
}

/** Point clignotant « en direct ». */
@Composable
fun BlinkDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val inf = rememberInfiniteTransition(label = "blink")
    val a by inf.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900, easing = Motion.Emphasized), RepeatMode.Reverse), label = "a")
    Box(modifier.size(size).alpha(a).clip(CircleShape).background(color))
}

// =============================================================================================
// Puces d'état

enum class StatusKind { Live, Busy, Idle, Good, Fair, Poor }

/** Puce d'état (20 dp) : point en direct, rotation en cours, ou niveau. */
@Composable
fun StatusChip(text: String, kind: StatusKind, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val acc = Accent.current
    val all = Accent.all
    val (bg, fg) = when (kind) {
        StatusKind.Live -> all.good.container to all.good.onContainer
        StatusKind.Good -> all.good.container to all.good.onContainer
        StatusKind.Fair -> all.fair.container to all.fair.onContainer
        StatusKind.Poor -> all.poor.container to all.poor.onContainer
        StatusKind.Busy -> (if (onHero) acc.accent else acc.container) to (if (onHero) acc.onAccent else acc.onContainer)
        StatusKind.Idle -> (if (onHero) acc.onContainer.copy(alpha = 0.10f) else cs.surfaceContainerHighest) to
            (if (onHero) acc.onContainer else cs.onSurfaceVariant)
    }
    val bgA by animateColorAsState(bg, tween(350, easing = Motion.Emphasized), label = "chipBg")
    val fgA by animateColorAsState(fg, tween(350, easing = Motion.Emphasized), label = "chipFg")
    Row(
        modifier.height(32.dp).clip(Corners.chip).background(bgA).padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (kind) {
            StatusKind.Live -> BlinkDot(fgA)
            StatusKind.Busy -> Spinner(12.dp, 2.dp, fgA)
            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(fgA.copy(alpha = 0.7f)))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fgA, maxLines = 1)
    }
}

// =============================================================================================
// Cartes

data class Metric(
    val label: String,
    val value: Double?,
    val unit: String,
    val decimals: Int = 0,
    val sym: Sym? = null,
    /** Avancement 0..1 pour une barre de niveau (null = aucune). */
    val level: Float? = null,
    val note: String? = null,
)

/** Carte héros (32 dp, fond accent.container) : puce d'état, grosse métrique en direct, petites tuiles. */
@Composable
fun HeroCard(
    title: String,
    value: Double?,
    decimals: Int,
    unit: String,
    modifier: Modifier = Modifier,
    chip: @Composable () -> Unit = {},
    badge: Sym? = null,
    tiles: List<Metric> = emptyList(),
) {
    val a = Accent.current
    Column(
        modifier.fillMaxWidth().fadeUp().clip(Corners.hero).background(a.container).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { chip() }
            if (badge != null) CookieBadge(badge, Modifier.pop(120), size = 48.dp, container = a.accent, content = a.onAccent)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = a.onContainer.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.Bottom) {
                LiveNumber(value, decimals, MetricStyle.hero, a.onContainer)
                Text(
                    unit, style = MaterialTheme.typography.titleLarge, color = a.onContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
        }
        if (tiles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { m ->
                    Column(
                        Modifier.weight(1f).clip(Corners.secondary).background(a.onContainer.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(m.label, style = MaterialTheme.typography.labelMedium, color = a.onContainer.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.Bottom) {
                            LiveNumber(m.value, m.decimals, MetricStyle.small, a.onContainer)
                            Text(m.unit, style = MaterialTheme.typography.labelMedium, color = a.onContainer.copy(alpha = 0.75f), modifier = Modifier.padding(start = 3.dp, bottom = 3.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Carte de section (24 dp, surfaceContainerLow) avec titre et icône. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    sym: Sym? = null,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(Corners.section).background(cs.surfaceContainerLow).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sym != null) IconContainer(sym)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                trailing()
            }
        }
        content()
    }
}

/** Conteneur d'icône (surfaceContainerHigh, icône à l'accent). */
@Composable
fun IconContainer(sym: Sym, size: Dp = 40.dp, filled: Boolean = true, tint: Color = Accent.current.accent) {
    Box(Modifier.size(size).clip(CircleShape).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Symbol(sym, size = size * 0.55f, tint = tint, filled = filled)
    }
}

/** Tuile de métrique (dans une carte de section). */
@Composable
fun MetricTile(m: Metric, modifier: Modifier = Modifier, levelColor: Color = Accent.current.accent) {
    Column(
        modifier.clip(Corners.secondary).background(cs.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.sym != null) Symbol(m.sym, size = 18.dp, tint = Accent.current.accent)
            Text(m.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            LiveNumber(m.value, m.decimals, MetricStyle.medium, cs.onSurface)
            Text(m.unit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp), maxLines = 1)
        }
        if (m.level != null) LevelBar(m.level, levelColor)
        if (m.note != null) Text(m.note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
    }
}

/** Liste clé / valeur. */
@Composable
fun KeyValueList(items: List<Pair<String, String>>) {
    Column {
        items.forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = 0.5f)))
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(v, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TNUM), color = cs.onSurface)
            }
        }
    }
}

data class LogEntry(val time: String, val text: String, val level: Level? = null)

/** Journal : horodatage + message, pastille colorée selon le niveau. */
@Composable
fun LogList(entries: List<LogEntry>, empty: String) {
    if (entries.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        return
    }
    val all = Accent.all
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { e ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val dot = e.level?.let { all.of(it).accent } ?: Accent.current.accent
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dot))
                Text(e.time, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TNUM), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                Text(e.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

// =============================================================================================
// Graphiques (Canvas)

/** Barre de niveau animée au ressort. */
@Composable
fun LevelBar(progress: Float, color: Color = Accent.current.accent, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.standard(), label = "level")
    val track = cs.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0.001f) drawRoundRect(color, size = Size(size.width * p.coerceAtLeast(size.height / size.width), size.height), cornerRadius = r)
    }
}

/** Jauge circulaire (270°) animée au ressort, avec un repère de seuil facultatif. */
@Composable
fun Gauge(progress: Float, modifier: Modifier = Modifier, color: Color = Accent.current.accent, mark: Float? = null, stroke: Dp = 14.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.standard(), label = "gauge")
    val track = cs.surfaceContainerHighest
    val markColor = Accent.all.poor.accent
    Canvas(modifier) {
        val sw = stroke.toPx()
        val d = size.minDimension - sw
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 135f, 270f, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        if (p > 0.002f) drawArc(color, 135f, 270f * p, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        if (mark != null) {
            val ang = Math.toRadians(135.0 + 270.0 * mark.coerceIn(0f, 1f))
            val c = Offset(size.width / 2, size.height / 2)
            val r1 = d / 2 - sw * 0.9f
            val r2 = d / 2 + sw * 0.9f
            drawLine(
                markColor,
                Offset(c.x + r1 * kotlin.math.cos(ang).toFloat(), c.y + r1 * kotlin.math.sin(ang).toFloat()),
                Offset(c.x + r2 * kotlin.math.cos(ang).toFloat(), c.y + r2 * kotlin.math.sin(ang).toFloat()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
}

private fun linePath(values: List<Float>, min: Float, max: Float, w: Float, h: Float, slots: Int): Path {
    val path = Path()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val step = w / (slots - 1).coerceAtLeast(1)
    val start = slots - values.size
    values.forEachIndexed { i, v ->
        val x = (start + i) * step
        val y = h - ((v - min) / span).coerceIn(0f, 1f) * h
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/** Courbe avec grille, remplissage léger et ligne de seuil facultative. */
@Composable
fun LineChart(
    values: List<Float>,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    color: Color = Accent.current.accent,
    slots: Int = 60,
    threshold: Float? = null,
) {
    val grid = cs.outlineVariant.copy(alpha = 0.6f)
    val thr = Accent.all.poor.accent
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width
        val h = size.height
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }
        if (threshold != null && max > min) {
            val y = h - ((threshold - min) / (max - min)).coerceIn(0f, 1f) * h
            drawLine(thr, Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        }
        if (values.size >= 2) {
            val line = linePath(values, min, max, w, h, slots)
            val fill = Path().apply {
                addPath(line)
                val step = w / (slots - 1).coerceAtLeast(1)
                lineTo(w.coerceAtMost((slots - 1) * step), h)
                lineTo((slots - values.size) * step, h)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.14f))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = values.last()
            val y = h - ((last - min) / ((max - min).takeIf { it > 0f } ?: 1f)).coerceIn(0f, 1f) * h
            drawCircle(color, 5.dp.toPx(), Offset(w, y))
        }
    }
}

/** Petite courbe sans axes. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = Accent.current.accent, slots: Int = 60) {
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max().let { if (it - min < 1e-3f) min + 1f else it }
        drawPath(linePath(values, min, max, size.width, size.height, slots), color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Histogramme simple (barres arrondies animées). */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, color: Color = Accent.current.accent) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1e-3f)
    Row(modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val p by animateFloatAsState(v / max, Motion.bouncy(), label = "bar$i")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(p.coerceIn(0.02f, 1f)).clip(Corners.small).background(color))
                }
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            }
        }
    }
}

// =============================================================================================
// Réglages : stepper, groupe segmenté, puces de filtre, interrupteur

/** Groupe segmenté (20 dp, conteneur avec padding 4 dp) ; l'indicateur glisse au ressort. */
@Composable
fun SegmentedGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, syms: List<Sym>? = null, enabled: Boolean = true) {
    val a = Accent.current
    val hap = LocalHaptics.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(48.dp).alpha(if (enabled) 1f else 0.38f).clip(Corners.chip).background(cs.surfaceContainerHigh).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, Motion.bouncy(), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCorner16).background(a.accent))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                val fg by animateColorAsState(if (sel) a.onAccent else cs.onSurfaceVariant, tween(250), label = "segFg")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCorner16)
                        .clickable(enabled = enabled, role = Role.Tab) { if (!sel) { hap.segment(); onSelect(i) } },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    syms?.getOrNull(i)?.let { Symbol(it, size = 18.dp, tint = fg, filled = sel); Spacer(Modifier.width(6.dp)) }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val RoundedCorner16 = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/** Stepper : − valeur + (boutons de 40 dp, retour haptique à chaque pas). */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true, sub: String? = null) {
    val hap = LocalHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        StepButton(Sym.Remove, minusEnabled) { hap.tick(); onMinus() }
        Text(
            value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM), color = cs.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.width(84.dp),
        )
        StepButton(Sym.Add, plusEnabled) { hap.tick(); onPlus() }
    }
}

@Composable
private fun StepButton(sym: Sym, enabled: Boolean, onClick: () -> Unit) {
    val a = Accent.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.bouncy(), label = "step")
    Box(
        Modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }.alpha(if (enabled) 1f else 0.38f)
            .clip(Corners.secondary).background(a.container)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(sym, size = 20.dp, tint = a.onContainer) }
}

/** Puces de filtre (20 dp) : coche quand la puce est sélectionnée. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChips(options: List<String>, selected: Set<Int>, onToggle: (Int) -> Unit) {
    val a = Accent.current
    val hap = LocalHaptics.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i in selected
            val bg by animateColorAsState(if (sel) a.container else cs.surfaceContainerHigh, tween(250), label = "chip")
            val fg by animateColorAsState(if (sel) a.onContainer else cs.onSurfaceVariant, tween(250), label = "chipFg")
            val pad by animateDpAsState(if (sel) 10.dp else 16.dp, Motion.standard(), label = "chipPad")
            Row(
                Modifier.height(40.dp).clip(Corners.chip).background(bg)
                    .clickable(role = Role.Checkbox) { hap.tick(); onToggle(i) }
                    .padding(start = pad, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (sel) Symbol(Sym.Check, Modifier.pop(), size = 18.dp, tint = fg)
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

/** Ligne de réglage avec interrupteur. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null, sym: Sym? = null, enabled: Boolean = true) {
    val a = Accent.current
    val hap = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f).clip(Corners.small)
            .clickable(enabled = enabled, role = Role.Switch) { hap.tick(); onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sym != null) IconContainer(sym, filled = checked)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = a.accent, checkedThumbColor = a.onAccent, checkedIconColor = a.accent),
        )
    }
}

// =============================================================================================
// Boutons

/** Bouton principal : 56 dp, 28 dp, pleine largeur ; s'enfonce au ressort. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sym: Sym? = null,
    enabled: Boolean = true,
    container: Color = Accent.current.accent,
    content: Color = Accent.current.onAccent,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.bouncy(), label = "press")
    val bg by animateColorAsState(container, tween(350, easing = Motion.Emphasized), label = "pbg")
    val fg by animateColorAsState(content, tween(350, easing = Motion.Emphasized), label = "pfg")
    Row(
        modifier.fillMaxWidth().height(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }.alpha(if (enabled) 1f else 0.38f)
            .clip(Corners.primaryButton).background(bg)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (sym != null) { Symbol(sym, size = 24.dp, tint = fg, filled = true); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}

/** Bouton Démarrer / Arrêter : passe au rouge sémantique pendant l'activité. */
@Composable
fun StartStopButton(running: Boolean, startText: String, stopText: String, onToggle: () -> Unit, enabled: Boolean = true) {
    val a = Accent.current
    val poor = Accent.all.poor
    val hap = LocalHaptics.current
    PrimaryButton(
        if (running) stopText else startText,
        { hap.confirm(); onToggle() },
        sym = if (running) Sym.Stop else Sym.PlayArrow,
        enabled = enabled,
        container = if (running) poor.accent else a.accent,
        content = if (running) poor.onAccent else a.onAccent,
    )
}

enum class ButtonStyle { Filled, Tonal, Outlined }

/** Bouton secondaire : 40 dp et 20 dp (ou 56 dp en version grande). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sym: Sym? = null,
    style: ButtonStyle = ButtonStyle.Tonal,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val a = Accent.current
    val (bg, fg) = when (style) {
        ButtonStyle.Filled -> a.accent to a.onAccent
        ButtonStyle.Tonal -> a.container to a.onContainer
        ButtonStyle.Outlined -> Color.Transparent to a.accent
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.bouncy(), label = "press2")
    val shape = if (large) Corners.primaryButton else Corners.secondary
    Row(
        modifier.height(if (large) 56.dp else 40.dp).graphicsLayer { scaleX = scale; scaleY = scale }.alpha(if (enabled) 1f else 0.38f)
            .clip(shape).background(bg)
            .then(if (style == ButtonStyle.Outlined) Modifier.border(BorderStroke(1.dp, cs.outline), shape) else Modifier)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (large) 24.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (sym != null) { Symbol(sym, size = if (large) 22.dp else 18.dp, tint = fg); Spacer(Modifier.width(8.dp)) }
        Text(text, style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Bouton icône rond (48 dp de zone tactile). */
@Composable
fun SymButton(sym: Sym, onClick: () -> Unit, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = cs.onSurfaceVariant, container: Color = Color.Transparent) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(container).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(sym, size = 24.dp, tint = tint, contentDescription = contentDescription) }
}

// =============================================================================================
// États

/** Panneau plein écran pour un blocage (permission manquante, aucun boîtier…). */
@Composable
fun StatePanel(sym: Sym, title: String, body: String, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PulseRing(Accent.current.accent, Modifier.size(160.dp))
            CookieBadge(sym, Modifier.pop(), size = 96.dp)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fadeUp(80))
        Text(
            body, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).fadeUp(140),
        )
        if (action != null) {
            Spacer(Modifier.height(32.dp))
            PrimaryButton(action, onAction, Modifier.fadeUp(200))
        }
    }
}

/** Carte d'état vide (ou d'attente). */
@Composable
fun EmptyStateCard(sym: Sym, title: String, body: String, modifier: Modifier = Modifier, busy: Boolean = false) {
    Row(
        modifier.fillMaxWidth().fadeUp().clip(Corners.section).background(cs.surfaceContainerLow).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) PulseRing(Accent.current.accent, Modifier.size(80.dp))
            CookieBadge(sym, Modifier.pop(), size = 56.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Bannière d'erreur (24 dp, errorContainer) avec « Réessayer ». */
@Composable
fun ErrorBanner(title: String, body: String, retry: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val fg = cs.onErrorContainer
    Column(
        modifier.fillMaxWidth().fadeUp().clip(Corners.section).background(cs.errorContainer).padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Symbol(Sym.Error, size = 24.dp, tint = fg, filled = true)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (retry != null) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Row(
                    Modifier.height(40.dp).clip(Corners.secondary).background(cs.error).clickable(role = Role.Button, onClick = onRetry).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Sym.Refresh, size = 18.dp, tint = cs.onError)
                    Text(retry, style = MaterialTheme.typography.labelLarge, color = cs.onError)
                }
            }
        }
    }
}

// =============================================================================================
// Outils, en-tête et dock

/** Tuile d'outil (24 dp) ; grisée avec le motif en sous-titre si l'outil est indisponible. */
@Composable
fun ToolTile(
    sym: Sym,
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    favorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val a = Accent.current
    val hap = LocalHaptics.current
    Column(
        modifier.heightIn(min = 132.dp).clip(Corners.section).background(cs.surfaceContainerLow)
            .combinedClickable(
                enabled = enabled, role = Role.Button,
                onLongClick = onLongClick?.let { { hap.longPress(); it() } },
                onClick = { hap.tick(); onClick() },
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).clip(Corners.small).background(if (enabled) a.container else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Symbol(sym, size = 26.dp, tint = if (enabled) a.onContainer else cs.onSurfaceVariant, filled = enabled)
            }
            Spacer(Modifier.weight(1f))
            if (favorite) Symbol(Sym.Star, Modifier.pop(), size = 20.dp, tint = a.accent, filled = true)
        }
        Text(name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** En-tête d'écran : grand titre, sous-titre et actions. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, backLabel: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) SymButton(Sym.ArrowBack, onBack, backLabel, tint = cs.onSurface)
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

data class DockTab(val sym: Sym, val label: String)

/** Dock flottant (28 dp) : bouton Accueil puis les onglets du domaine, à l'accent courant. */
@Composable
fun Dock(
    homeLabel: String,
    tabs: List<DockTab>,
    selected: Int?,
    onHome: () -> Unit,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = Accent.current
    val hap = LocalHaptics.current
    Row(
        modifier.graphicsLayer { shadowElevation = 6.dp.toPx(); shape = Corners.dock; clip = true }
            .background(cs.surfaceContainerHigh).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(Corners.secondary).background(a.accent).clickable(role = Role.Button) { hap.segment(); onHome() },
            contentAlignment = Alignment.Center,
        ) { Symbol(Sym.Home, size = 24.dp, tint = a.onAccent, filled = true, contentDescription = homeLabel) }
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) a.container else Color.Transparent, tween(300, easing = Motion.Emphasized), label = "tab")
            val fg by animateColorAsState(if (sel) a.onContainer else cs.onSurfaceVariant, tween(300, easing = Motion.Emphasized), label = "tabFg")
            val w by animateDpAsState(if (sel) 112.dp else 64.dp, Motion.bouncy(), label = "tabW")
            Row(
                Modifier.height(52.dp).width(w).clip(Corners.secondary).background(bg)
                    .clickable(role = Role.Tab) { if (!sel) hap.segment(); onTab(i) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Symbol(t.sym, size = 24.dp, tint = fg, filled = sel, contentDescription = if (sel) null else t.label)
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Fournit une couleur de contenu par défaut. */
@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color, content = content)
