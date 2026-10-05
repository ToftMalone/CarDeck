package com.cardeck.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texte avec les tailles/poids de la maquette. */
@Composable
fun T(
    text: String,
    size: Int = 14,
    color: Color = Cd.c.on,
    weight: Int = 400,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    align: TextAlign? = null,
    lineHeight: Int = (size * 1.4f).toInt(),
    maxLines: Int = Int.MAX_VALUE,
    spacing: Float = 0f,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
        fontFamily = if (mono) FontFamily.Monospace else null,
        textAlign = align,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        letterSpacing = spacing.sp,
        style = TextStyle.Default,
    )
}

@Composable
fun Ico(icon: ImageVector, size: Int = 24, tint: Color = Cd.c.on, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size.dp))
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = Cd.c.on, bg: Color = Color.Transparent, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 24, tint) }
}

/** Barre supérieure avec bouton retour ; le titre apparaît quand la page défile. */
@Composable
fun BackBar(title: String?, scrolled: Boolean, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val bg by animateColorAsState(if (scrolled) Cd.c.sf2 else Cd.c.sf, tween(250), label = "bar")
    val titleAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(200), label = "barT")
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(bg).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
        if (title != null) T(title, 22, mono = false, modifier = Modifier.weight(1f).alpha(titleAlpha), lineHeight = 28) else Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Column(Modifier.padding(top = 4.dp)) {
        T(title, 32, weight = 400, lineHeight = 40)
        if (sub != null) T(sub, 14, Cd.c.onv, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    T(text, 14, Cd.c.p, 500, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}

/** Puce de filtre (M3 FilterChip). */
@Composable
fun CdChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.sc else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) Color.Transparent else c.olv), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = if (selected) 8.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Ico(Icons.Rounded.Check, 18, c.osc)
        T(label, 14, if (selected) c.osc else c.onv, 500)
    }
}

/** Bouton segmenté (2 ou 3 options). */
@Composable
fun Segmented(options: List<Triple<String, ImageVector?, Boolean>>, onSelect: (Int) -> Unit) {
    val c = Cd.c
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.ol, RoundedCornerShape(20.dp))) {
        options.forEachIndexed { i, (label, icon, sel) ->
            val bg by animateColorAsState(if (sel) c.sc else Color.Transparent, tween(200), label = "seg")
            if (i > 0) Box(Modifier.width(1.dp).fillMaxSize().background(c.ol))
            Row(
                Modifier.weight(1f).fillMaxSize().background(bg).clickable { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) { Ico(icon, 18, if (sel) c.osc else c.on); Spacer(Modifier.width(6.dp)) }
                T(label, 14, if (sel) c.osc else c.on, 500)
            }
        }
    }
}

@Composable
fun CdSwitch(checked: Boolean, onChange: () -> Unit) {
    val c = Cd.c
    Switch(
        checked = checked,
        onCheckedChange = { onChange() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.op, checkedTrackColor = c.p, checkedBorderColor = c.p,
            uncheckedThumbColor = c.ol, uncheckedTrackColor = c.sf4, uncheckedBorderColor = c.ol,
        ),
    )
}

@Composable
fun FilledBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Cd.c
    Box(
        modifier.fillMaxWidth().height(56.dp).alpha(if (enabled) 1f else .38f).clip(RoundedCornerShape(28.dp)).background(c.p)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 16, c.op, 500) }
}

@Composable
fun TextBtn(text: String, onClick: () -> Unit, icon: ImageVector? = null, color: Color = Cd.c.p, weight: Int = 500) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Ico(icon, 18, color)
        T(text, 14, color, weight)
    }
}

/** Champ de saisie « outlined » M3. */
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, mono: Boolean = false, number: Boolean = false) {
    val c = Cd.c
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else KeyboardOptions.Default,
        textStyle = TextStyle(fontSize = 16.sp, fontFamily = if (mono) FontFamily.Monospace else null, letterSpacing = if (mono) 1.sp else 0.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = c.p, unfocusedIndicatorColor = c.ol,
            focusedLabelColor = c.p, unfocusedLabelColor = c.onv,
            focusedTextColor = c.on, unfocusedTextColor = c.on, cursorColor = c.p,
        ),
    )
}

/** Jauge circulaire à 270° (viewBox 100×100, r=42 comme la maquette). */
@Composable
fun ArcGauge(progress: Float, color: Color, strokeUnits: Float, modifier: Modifier = Modifier, durationMs: Int = 900) {
    val c = Cd.c
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(durationMs, easing = FastOutSlowInEasing), label = "gauge")
    Canvas(modifier) {
        val k = size.minDimension / 100f
        val r = 42f * k
        val sw = strokeUnits * k
        val tl = Offset(size.width / 2 - r, size.height / 2 - r)
        val sz = Size(r * 2, r * 2)
        drawArc(c.sf4, 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        if (p > 0.001f) drawArc(color, 135f, 270f * p, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Radar animé (appairage / scan). */
@Composable
fun Radar(size: Dp, active: Boolean, icon: ImageVector? = null, label: String? = null) {
    val c = Cd.c
    val inf = rememberInfiniteTransition(label = "radar")
    val pulses = (0..2).map { i ->
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), initialStartOffset = StartOffset(i * 800)), label = "p$i")
    }
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "spin")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            if (active) {
                pulses.forEach { st ->
                    val v = st.value
                    drawCircle(c.p.copy(alpha = (.8f * (1 - v)).coerceIn(0f, 1f)), radius = r * (.35f + .65f * v), style = Stroke(2.dp.toPx()))
                }
                rotate(spin) {
                    drawCircle(
                        brush = Brush.sweepGradient(0f to Color.Transparent, .72f to Color.Transparent, 1f to c.p.copy(alpha = .4f)),
                        radius = r * .8f,
                    )
                }
            }
            drawCircle(c.olv, radius = r * .64f, style = Stroke(1.dp.toPx()))
        }
        Box(
            Modifier.size(size * .4f).clip(CircleShape).background(c.p),
            contentAlignment = Alignment.Center,
        ) {
            if (label != null) T(label, 22, c.op, 600)
            else if (icon != null) Ico(icon, (size.value * .17f).toInt(), c.op)
        }
    }
}

@Composable
fun Spinner(size: Dp = 24.dp, width: Dp = 3.dp, color: Color = Cd.c.p) {
    val inf = rememberInfiniteTransition(label = "spin")
    val a by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size).rotate(a)) {
        drawArc(color, 0f, 270f, false, style = Stroke(width.toPx(), cap = StrokeCap.Butt), topLeft = Offset(width.toPx() / 2, width.toPx() / 2), size = Size(this.size.width - width.toPx(), this.size.height - width.toPx()))
    }
}

/** Bloc « squelette » clignotant. */
@Composable
fun Skeleton(modifier: Modifier, radius: Dp = 6.dp) {
    val inf = rememberInfiniteTransition(label = "sk")
    val a by inf.animateFloat(.45f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Box(modifier.alpha(a).clip(RoundedCornerShape(radius)).background(Cd.c.sf4))
}

@Composable
fun ConnDot() {
    val inf = rememberInfiniteTransition(label = "dot")
    val a by inf.animateFloat(1f, .3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(Cd.c.g))
}

