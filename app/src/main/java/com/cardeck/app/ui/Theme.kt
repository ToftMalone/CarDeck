package com.cardeck.app.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.google.android.material.color.MaterialColors

enum class ThemeMode { System, Light, Dark }

/** Palette statique de secours (sans couleur dynamique). */
enum class Palette(val seed: Color) {
    Rose(Color(0xFF8C4A5A)),
    Blue(Color(0xFF455E83)),
}

/** Accents de base, harmonisés ensuite avec la couleur primaire. */
object AccentSeeds {
    val Blue = Color(0xFF3A5BA9)
    val Violet = Color(0xFF5E4DB2)
    val Green = Color(0xFF2F6A3E)
    val Ochre = Color(0xFF7F5700)
    val Teal = Color(0xFF006A6A)
    val Brick = Color(0xFFA0412D)

    // Couleurs sémantiques d'état.
    val Good = Color(0xFF2E7D32)
    val Fair = Color(0xFFB26A00)
    val Poor = Color(0xFFBA1A1A)
}

/** Domaines fonctionnels : chacun a son accent. */
enum class Domain(val seed: Color, val sym: Sym) {
    Drive(AccentSeeds.Blue, Sym.Speed),
    Diagnostic(AccentSeeds.Brick, Sym.Stethoscope),
    Trips(AccentSeeds.Green, Sym.Route),
    Garage(AccentSeeds.Violet, Sym.DirectionsCar),
}

/** Rôles d'un accent (équivalent de MaterialColors.getColorRoles). */
@Immutable
data class AccentRoles(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

enum class Level { Good, Fair, Poor }

@Immutable
data class Accents(
    val domains: Map<Domain, AccentRoles>,
    val primary: AccentRoles,
    val good: AccentRoles,
    val fair: AccentRoles,
    val poor: AccentRoles,
) {
    fun of(d: Domain?) = if (d == null) primary else domains.getValue(d)
    fun of(l: Level) = when (l) { Level.Good -> good; Level.Fair -> fair; Level.Poor -> poor }
}

/** Accent harmonisé avec la couleur primaire (dynamique ou statique). */
fun accentRoles(seed: Color, primary: Color, light: Boolean): AccentRoles {
    val h = MaterialColors.harmonize(seed.toArgb(), primary.toArgb())
    val r = MaterialColors.getColorRoles(h, light)
    return AccentRoles(Color(r.accent), Color(r.onAccent), Color(r.accentContainer), Color(r.onAccentContainer))
}

fun accentsFor(scheme: ColorScheme, light: Boolean): Accents {
    val p = scheme.primary
    return Accents(
        domains = Domain.entries.associateWith { accentRoles(it.seed, p, light) },
        primary = AccentRoles(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer),
        good = accentRoles(AccentSeeds.Good, p, light),
        fair = accentRoles(AccentSeeds.Fair, p, light),
        poor = accentRoles(AccentSeeds.Poor, p, light),
    )
}

val LocalAccents = staticCompositionLocalOf { accentsFor(lightRose, true) }

/** Accent de la section active. */
val LocalAccent = compositionLocalOf { accentsFor(lightRose, true).primary }

object Accent {
    val current: AccentRoles
        @Composable @ReadOnlyComposable get() = LocalAccent.current
    val all: Accents
        @Composable @ReadOnlyComposable get() = LocalAccents.current
}

/** Fournit l'accent d'un domaine (null = couleur primaire), avec un changement animé (350 ms). */
@Composable
fun ProvideAccent(domain: Domain?, content: @Composable () -> Unit) {
    val target = LocalAccents.current.of(domain)
    ProvideAccentRoles(target, content)
}

@Composable
fun ProvideAccentRoles(target: AccentRoles, content: @Composable () -> Unit) {
    val spec = tween<Color>(350, easing = Motion.Emphasized)
    val a by animateColorAsState(target.accent, spec, label = "accent")
    val oa by animateColorAsState(target.onAccent, spec, label = "onAccent")
    val c by animateColorAsState(target.container, spec, label = "container")
    val oc by animateColorAsState(target.onContainer, spec, label = "onContainer")
    val roles = AccentRoles(a, oa, c, oc)
    val base = LocalCd.current
    CompositionLocalProvider(
        LocalAccent provides roles,
        LocalCd provides base.copy(p = a, op = oa, pc = c, opc = oc),
        content = content,
    )
}

/** Mélange deux schémas de couleurs (transition animée du thème). */
private fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme {
    if (t >= 1f) return b
    fun l(x: Color, y: Color) = lerp(x, y, t)
    return a.copy(
        primary = l(a.primary, b.primary), onPrimary = l(a.onPrimary, b.onPrimary),
        primaryContainer = l(a.primaryContainer, b.primaryContainer), onPrimaryContainer = l(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = l(a.inversePrimary, b.inversePrimary),
        secondary = l(a.secondary, b.secondary), onSecondary = l(a.onSecondary, b.onSecondary),
        secondaryContainer = l(a.secondaryContainer, b.secondaryContainer), onSecondaryContainer = l(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = l(a.tertiary, b.tertiary), onTertiary = l(a.onTertiary, b.onTertiary),
        tertiaryContainer = l(a.tertiaryContainer, b.tertiaryContainer), onTertiaryContainer = l(a.onTertiaryContainer, b.onTertiaryContainer),
        background = l(a.background, b.background), onBackground = l(a.onBackground, b.onBackground),
        surface = l(a.surface, b.surface), onSurface = l(a.onSurface, b.onSurface),
        surfaceVariant = l(a.surfaceVariant, b.surfaceVariant), onSurfaceVariant = l(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = l(a.surfaceTint, b.surfaceTint),
        inverseSurface = l(a.inverseSurface, b.inverseSurface), inverseOnSurface = l(a.inverseOnSurface, b.inverseOnSurface),
        error = l(a.error, b.error), onError = l(a.onError, b.onError),
        errorContainer = l(a.errorContainer, b.errorContainer), onErrorContainer = l(a.onErrorContainer, b.onErrorContainer),
        outline = l(a.outline, b.outline), outlineVariant = l(a.outlineVariant, b.outlineVariant), scrim = l(a.scrim, b.scrim),
        surfaceBright = l(a.surfaceBright, b.surfaceBright), surfaceDim = l(a.surfaceDim, b.surfaceDim),
        surfaceContainer = l(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = l(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = l(a.surfaceContainerHighest, b.surfaceContainerHighest),
        surfaceContainerLow = l(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainerLowest = l(a.surfaceContainerLowest, b.surfaceContainerLowest),
    )
}

/** Schéma courant, avec une transition de 400 ms quand le thème change. */
@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = lerp(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(400, easing = Motion.Emphasized))
    }
    return lerp(from, to, progress.value)
}

fun dynamicColorAvailable() = Build.VERSION.SDK_INT >= 31

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CarDeckTheme(
    mode: ThemeMode,
    dynamic: Boolean,
    palette: Palette,
    haptics: Boolean,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val dark = isDark(mode)
    val useDynamic = dynamic && dynamicColorAvailable()
    val target = remember(dark, useDynamic, palette) {
        when {
            useDynamic && dark -> dynamicDarkColorScheme(ctx)
            useDynamic -> dynamicLightColorScheme(ctx)
            palette == Palette.Rose -> if (dark) darkRose else lightRose
            else -> if (dark) darkBlue else lightBlue
        }
    }
    // Les accents sont calculés une fois pour le schéma cible, puis animés par ProvideAccent.
    val accents = remember(target) { accentsFor(target, !dark) }
    val scheme = animatedScheme(target)
    val view = LocalView.current
    val hap = remember(view, haptics) { Haptics(view, haptics) }
    CompositionLocalProvider(
        LocalAccents provides accents,
        LocalHaptics provides hap,
        LocalCd provides cdColorsOf(scheme, accents, dark),
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = CarDeckTypography,
            shapes = CarDeckShapes,
        ) {
            ProvideAccentRoles(accents.primary, content)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Jetons hérités : les écrans pas encore refaits lisent ces couleurs, désormais dérivées du schéma
// Material 3 et de l'accent courant.

data class CdColors(
    val p: Color, val op: Color, val pc: Color, val opc: Color,
    val s: Color, val os: Color, val sc: Color, val osc: Color,
    val t: Color, val ot: Color, val tc: Color, val otc: Color,
    val sf: Color, val sfl: Color, val sf1: Color, val sf2: Color, val sf3: Color, val sf4: Color,
    val on: Color, val onv: Color, val ol: Color, val olv: Color,
    val isf: Color, val ion: Color,
    val e: Color, val oe: Color, val ec: Color, val oec: Color,
    val w: Color, val wc: Color, val owc: Color,
    val g: Color, val gc: Color, val ogc: Color,
    val dark: Boolean,
)

private fun cdColorsOf(s: ColorScheme, a: Accents, dark: Boolean) = CdColors(
    p = s.primary, op = s.onPrimary, pc = s.primaryContainer, opc = s.onPrimaryContainer,
    s = s.secondary, os = s.onSecondary, sc = s.secondaryContainer, osc = s.onSecondaryContainer,
    t = s.tertiary, ot = s.onTertiary, tc = s.tertiaryContainer, otc = s.onTertiaryContainer,
    sf = s.background, sfl = s.surfaceContainerLowest, sf1 = s.surfaceContainerLow, sf2 = s.surfaceContainer,
    sf3 = s.surfaceContainerHigh, sf4 = s.surfaceContainerHighest,
    on = s.onSurface, onv = s.onSurfaceVariant, ol = s.outline, olv = s.outlineVariant,
    isf = s.inverseSurface, ion = s.inverseOnSurface,
    e = s.error, oe = s.onError, ec = s.errorContainer, oec = s.onErrorContainer,
    w = a.fair.accent, wc = a.fair.container, owc = a.fair.onContainer,
    g = a.good.accent, gc = a.good.container, ogc = a.good.onContainer,
    dark = dark,
)

val LocalCd = staticCompositionLocalOf { cdColorsOf(lightRose, accentsFor(lightRose, true), false) }

object Cd {
    val c: CdColors
        @Composable @ReadOnlyComposable get() = LocalCd.current
}

/** Raccourci vers le schéma Material courant. */
val cs: ColorScheme
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
