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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
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
    // Google Sans Flex pour les grands textes (titres, valeurs en direct, chiffres alignés), Roboto Flex pour le reste.
    val big = size >= 20
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
        fontFamily = if (mono) FontFamily.Monospace else if (big) GoogleSansFlex else RobotoFlex,
        textAlign = align,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        letterSpacing = spacing.sp,
        style = TextStyle(fontFeatureSettings = if (big && !mono) "tnum" else null),
    )
}

@Composable
fun Ico(icon: ImageVector, size: Int = 24, tint: Color = Cd.c.on, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size.dp))
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = Cd.c.on, bg: Color = Color.Transparent, modifier: Modifier = Modifier) {
    val haptics = Cd.haptics
    Box(
        modifier.size(48.dp).clip(CircleShape).background(bg).clickable { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 24, tint) }
}

/** Barre du haut des pages : retour, titre (Google Sans Flex 22) et, une fois la page défilée, une teinte de surface. */
@Composable
fun BackBar(title: String?, scrolled: Boolean, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val bg by animateColorAsState(if (scrolled) Cd.c.sf2 else Cd.c.sf, tween(250, easing = Motion.Emphasized), label = "bar")
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(bg).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
        if (title != null) Text(title, Modifier.weight(1f), style = gs(22, 28, 500), color = Cd.c.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Sous-titre de page (le titre est dans la barre du haut, comme dans All Radio Tools). */
@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    if (sub != null) Text(sub, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp), style = rf(14, 20), color = Cd.c.onv)
    else Spacer(Modifier.height(8.dp))
}

/** Titre de section, couleur de l'accent de la rubrique. */
@Composable
fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), style = rf(14, 20, 600), color = Cd.acc.accent)
}

/** Puce de filtre M3 : coche et conteneur de l'accent une fois choisie (200 ms). */
@Composable
fun CdChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    val bg by animateColorAsState(if (selected) acc.container else Color.Transparent, tween(200, easing = Motion.Emphasized), label = "chip")
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(BorderStroke(1.dp, if (selected) Color.Transparent else c.olv), RoundedCornerShape(8.dp))
            .clickable { haptics.tick(); onClick() }
            .padding(start = if (selected) 8.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selected) Ico(Icons.Rounded.Check, 18, acc.onContainer)
        Text(label, style = rf(13, 18, 600), color = if (selected) acc.onContainer else c.onv, maxLines = 1)
    }
}

/** Choix unique en pilule : conteneur arrondi, choix rempli de l'accent (250 ms). */
@Composable
fun Segmented(options: List<Triple<String, ImageVector?, Boolean>>, onSelect: (Int) -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf3).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, (label, icon, sel) ->
            val bg by animateColorAsState(if (sel) acc.accent else Color.Transparent, tween(250, easing = Motion.Emphasized), label = "seg")
            val fg by animateColorAsState(if (sel) acc.onAccent else c.onv, tween(250, easing = Motion.Emphasized), label = "segFg")
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                    .clickable { if (!sel) { haptics.segment(); onSelect(i) } }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) { Ico(icon, 18, fg); Spacer(Modifier.width(4.dp)) }
                Text(label, style = rf(14, 18, 600), color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
fun CdSwitch(checked: Boolean, onChange: () -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    Switch(
        checked = checked,
        onCheckedChange = { haptics.tick(); onChange() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = acc.onAccent, checkedTrackColor = acc.accent, checkedBorderColor = acc.accent,
            uncheckedThumbColor = c.ol, uncheckedTrackColor = c.sf4, uncheckedBorderColor = c.ol,
        ),
    )
}

/** Bouton pilule de 56 dp, rempli de l'accent. */
@Composable
fun FilledBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (enabled) acc.accent else c.on.copy(alpha = 0.12f))
            .clickable(enabled = enabled) { haptics.confirm(); onClick() }.padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { Text(text, style = rf(16, 20, 600), color = if (enabled) acc.onAccent else c.on.copy(alpha = 0.38f), maxLines = 1) }
}

/** Action en texte (couleur de l'accent). */
@Composable
fun TextBtn(text: String, onClick: () -> Unit, icon: ImageVector? = null, color: Color = Cd.acc.accent, weight: Int = 600) {
    val haptics = Cd.haptics
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable { haptics.tick(); onClick() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Ico(icon, 18, color)
        Text(text, style = rf(14, 18, weight), color = color)
    }
}

/** Carte des maquettes : surfaceContainerLow, coins de 24 dp. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Cd.c.sf1,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(shape).background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

/** Pastille d'état : point coloré (clignotant pendant une opération) sur la surface. */
@Composable
fun StatusChip(text: String, dot: Color, blink: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = Cd.c
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(c.sf)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (blink) BlinkDot(dot, 8.dp) else Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = rf(12, 16, 500), color = c.on, maxLines = 1)
    }
}

/** Champ de saisie « outlined » M3. */
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, mono: Boolean = false, number: Boolean = false, password: Boolean = false) {
    val c = Cd.c
    val acc = Cd.acc
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = when {
            password -> KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false)
            number -> KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            else -> KeyboardOptions.Default
        },
        textStyle = TextStyle(fontSize = 16.sp, fontFamily = if (mono) FontFamily.Monospace else RobotoFlex, letterSpacing = if (mono) 1.sp else 0.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = acc.accent, unfocusedIndicatorColor = c.ol,
            focusedLabelColor = acc.accent, unfocusedLabelColor = c.onv,
            focusedTextColor = c.on, unfocusedTextColor = c.on, cursorColor = acc.accent,
        ),
    )
}

/** Jauge circulaire à 270° (viewBox 100×100, r=42 comme la maquette). */
@Composable
fun ArcGauge(progress: Float, color: Color, strokeUnits: Float, modifier: Modifier = Modifier, durationMs: Int = 900) {
    val c = Cd.c
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.standard(), label = "gauge")
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
    val c = Cd.c.let { cc -> Cd.acc.let { a -> cc.copy(p = a.accent, op = a.onAccent) } }
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
fun Spinner(size: Dp = 24.dp, width: Dp = 3.dp, color: Color = Cd.acc.accent) {
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
fun ConnDot() = BlinkDot(Cd.c.g, 8.dp)

