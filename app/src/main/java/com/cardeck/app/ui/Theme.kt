package com.cardeck.app.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cardeck.app.R
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Système de design repris d'All Radio Tools : palettes Material 3 (couleurs du fond d'écran, Rose, Neutre), polices variables
 * Google Sans Flex et Roboto Flex, formes, mouvements (ressorts, courbe « emphasized ») et couleurs d'accent par rubrique.
 */
enum class Palette(val label: String) {
    /** Couleurs tirées du fond d'écran (Android 12 et plus) ; Rose avant. */
    Dynamique("Fond d'écran"),
    Rose("Rose"),
    Neutre("Neutre"),
}

enum class ThemeMode { Light, Dark, System }

/** Les couleurs dynamiques existent depuis Android 12. */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= 31

// ---------------------------------------------------------------- Palettes (graine Rose #8C4A5A, et base neutre)

val RoseLight = lightColorScheme(
    primary = Color(0xFF8C4A5A), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E0), onPrimaryContainer = Color(0xFF3A0718),
    secondary = Color(0xFF75565C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E0), onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7B5733), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE0), onSurfaceVariant = Color(0xFF524345),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCF0F1),
    surfaceContainer = Color(0xFFF6EAEB), surfaceContainerHigh = Color(0xFFF1E4E5),
    surfaceContainerHighest = Color(0xFFEBDEE0),
    outline = Color(0xFF847375), outlineVariant = Color(0xFFD7C1C4),
    inverseSurface = Color(0xFF382E30), inverseOnSurface = Color(0xFFFEEDEF),
    inversePrimary = Color(0xFFFFB1C1),
)

val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C1), onPrimary = Color(0xFF551D2D),
    primaryContainer = Color(0xFF713343), onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFE4BDC3), onSecondary = Color(0xFF43292E),
    secondaryContainer = Color(0xFF5C3F44), onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFFEDBD91), onTertiary = Color(0xFF462A09),
    tertiaryContainer = Color(0xFF60401E), onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191113), onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF191113), onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF524345), onSurfaceVariant = Color(0xFFD7C1C4),
    surfaceContainerLowest = Color(0xFF140C0E), surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF261D1F), surfaceContainerHigh = Color(0xFF312829),
    surfaceContainerHighest = Color(0xFF3C3234),
    outline = Color(0xFF9F8C8F), outlineVariant = Color(0xFF524345),
    inverseSurface = Color(0xFFEFDFE1), inverseOnSurface = Color(0xFF382E30),
    inversePrimary = Color(0xFF8C4A5A),
)

val NeutralLight = RoseLight.copy(
    primary = Color(0xFF455E83), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF565F71), secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    background = Color(0xFFF8F9FB), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FB), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainer = Color(0xFFECEEF2), surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE0E2E6),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
    inverseSurface = Color(0xFF2E3035), inverseOnSurface = Color(0xFFF0F0F7),
    inversePrimary = Color(0xFFADC6EE),
)

val NeutralDark = RoseDark.copy(
    primary = Color(0xFFADC6EE), onPrimary = Color(0xFF133053),
    primaryContainer = Color(0xFF2D476A), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC), secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2E3035),
    inversePrimary = Color(0xFF455E83),
)

// ---------------------------------------------------------------- Accents par rubrique

/** Les 4 rôles d'une couleur d'accent : accent, texte sur accent, conteneur, texte sur conteneur. */
@Immutable
data class AccentRoles(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

/** Rubriques de CarDeck : chacune a sa couleur, comme chaque réseau d'All Radio Tools. */
enum class Section { Dash, Obd, Diag, Trips, Adapter, Settings, Explorer, Perf }

private val AccentsLight = mapOf(
    Section.Dash to AccentRoles(Color(0xFF3A5BA9), Color.White, Color(0xFFDAE2FF), Color(0xFF001946)),
    Section.Obd to AccentRoles(Color(0xFF006A6A), Color.White, Color(0xFF9CF1F0), Color(0xFF002020)),
    Section.Diag to AccentRoles(Color(0xFFA0412D), Color.White, Color(0xFFFFDAD3), Color(0xFF3E0500)),
    Section.Trips to AccentRoles(Color(0xFF2F6A3E), Color.White, Color(0xFFB2F1BA), Color(0xFF00210C)),
    Section.Adapter to AccentRoles(Color(0xFF9A4521), Color.White, Color(0xFFFFDBCE), Color(0xFF370E00)),
    Section.Settings to AccentRoles(Color(0xFF7F5700), Color.White, Color(0xFFFFDEA6), Color(0xFF281800)),
    Section.Explorer to AccentRoles(Color(0xFF5E4DB2), Color.White, Color(0xFFE6DEFF), Color(0xFF1A0063)),
    Section.Perf to AccentRoles(Color(0xFF984061), Color.White, Color(0xFFFFD9E2), Color(0xFF3E001D)),
)

private val AccentsDark = mapOf(
    Section.Dash to AccentRoles(Color(0xFFB1C5FF), Color(0xFF002C71), Color(0xFF23438F), Color(0xFFDAE2FF)),
    Section.Obd to AccentRoles(Color(0xFF80D5D4), Color(0xFF003737), Color(0xFF004F4F), Color(0xFF9CF1F0)),
    Section.Diag to AccentRoles(Color(0xFFFFB4A5), Color(0xFF611205), Color(0xFF802A18), Color(0xFFFFDAD3)),
    Section.Trips to AccentRoles(Color(0xFF97D5A0), Color(0xFF003919), Color(0xFF155129), Color(0xFFB2F1BA)),
    Section.Adapter to AccentRoles(Color(0xFFFFB599), Color(0xFF5A1C00), Color(0xFF7B2E0B), Color(0xFFFFDBCE)),
    Section.Settings to AccentRoles(Color(0xFFF8BD49), Color(0xFF432C00), Color(0xFF604100), Color(0xFFFFDEA6)),
    Section.Explorer to AccentRoles(Color(0xFFCBBEFF), Color(0xFF2F1A83), Color(0xFF46339A), Color(0xFFE6DEFF)),
    Section.Perf to AccentRoles(Color(0xFFFFB1C8), Color(0xFF5E1133), Color(0xFF7B2949), Color(0xFFFFD9E2)),
)

fun accentRoles(section: Section, dark: Boolean): AccentRoles = (if (dark) AccentsDark else AccentsLight).getValue(section)

// ---------------------------------------------------------------- Jetons de CarDeck

/** Jetons de couleur de l'application (rôles Material 3, noms courts). */
@Immutable
data class CdColors(
    val p: Color, val op: Color, val pc: Color, val opc: Color,
    val s: Color, val os: Color, val sc: Color, val osc: Color,
    val t: Color, val ot: Color, val tc: Color, val otc: Color,
    val sf: Color, val sfl: Color, val sf1: Color, val sf2: Color, val sf3: Color, val sf4: Color,
    val on: Color, val onv: Color, val ol: Color, val olv: Color,
    val isf: Color, val ion: Color, val ip: Color,
    val e: Color, val oe: Color, val ec: Color, val oec: Color,
    val w: Color, val wc: Color, val owc: Color,
    val g: Color, val gc: Color, val ogc: Color,
    /** Ombre des éléments flottants (cartes choisies, dock). */
    val shadow: Color,
    val dark: Boolean,
)

/** Jetons tirés d'un schéma Material 3 ; « attention » et « succès » repris des accents ambre et vert. */
fun cdColors(s: ColorScheme, dark: Boolean): CdColors = CdColors(
    p = s.primary, op = s.onPrimary, pc = s.primaryContainer, opc = s.onPrimaryContainer,
    s = s.secondary, os = s.onSecondary, sc = s.secondaryContainer, osc = s.onSecondaryContainer,
    t = s.tertiary, ot = s.onTertiary, tc = s.tertiaryContainer, otc = s.onTertiaryContainer,
    sf = s.surface, sfl = s.surfaceContainerLowest, sf1 = s.surfaceContainerLow, sf2 = s.surfaceContainer,
    sf3 = s.surfaceContainerHigh, sf4 = s.surfaceContainerHighest,
    on = s.onSurface, onv = s.onSurfaceVariant, ol = s.outline, olv = s.outlineVariant,
    isf = s.inverseSurface, ion = s.inverseOnSurface, ip = s.inversePrimary,
    e = s.error, oe = s.onError, ec = s.errorContainer, oec = s.onErrorContainer,
    w = if (dark) Color(0xFFF8BD49) else Color(0xFF8A5A00),
    wc = accentRoles(Section.Settings, dark).container, owc = accentRoles(Section.Settings, dark).onContainer,
    g = if (dark) Color(0xFF97D5A0) else Color(0xFF2F6A3E),
    gc = accentRoles(Section.Trips, dark).container, ogc = accentRoles(Section.Trips, dark).onContainer,
    shadow = if (dark) Color(0x99000000) else Color(0x47461423),
    dark = dark,
)

val LocalCd = staticCompositionLocalOf { cdColors(RoseDark, true) }

/** Accent de la rubrique affichée (variables --acc des maquettes) ; par défaut, la couleur principale. */
val LocalAccent = staticCompositionLocalOf { AccentRoles(RoseDark.primary, RoseDark.onPrimary, RoseDark.primaryContainer, RoseDark.onPrimaryContainer) }

object Cd {
    val c: CdColors
        @Composable @ReadOnlyComposable get() = LocalCd.current

    /** Accent de la rubrique en cours. */
    val acc: AccentRoles
        @Composable @ReadOnlyComposable get() = LocalAccent.current

    val haptics: Haptics
        @Composable @ReadOnlyComposable get() = LocalHaptics.current
}

/** Fournit l'accent de [section] au contenu (transition de couleur de 350 ms). */
@Composable
fun ProvideAccent(section: Section?, content: @Composable () -> Unit) {
    val c = Cd.c
    val roles = section?.let { accentRoles(it, c.dark) } ?: AccentRoles(c.p, c.op, c.pc, c.opc)
    val spec = tween<Color>(350, easing = Motion.Emphasized)
    val a by animateColorAsState(roles.accent, spec, label = "acc")
    val oa by animateColorAsState(roles.onAccent, spec, label = "onAcc")
    val ct by animateColorAsState(roles.container, spec, label = "accC")
    val oc by animateColorAsState(roles.onContainer, spec, label = "onAccC")
    val roleSet = AccentRoles(a, oa, ct, oc)
    // Dans une rubrique, la couleur principale devient son accent : jauges, boutons et liens prennent sa couleur.
    val tinted = if (section != null) c.copy(p = a, op = oa, pc = ct, opc = oc) else c
    CompositionLocalProvider(LocalAccent provides roleSet, LocalCd provides tinted, content = content)
}

// ---------------------------------------------------------------- Typographie

private val Weights = listOf(300, 400, 500, 600, 700, 800)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(res: Int) = FontFamily(
    Weights.map { w -> Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

/** Google Sans Flex : grands titres et valeurs en direct. */
val GoogleSansFlex = variableFamily(R.font.google_sans_flex)

/** Roboto Flex : titres de cartes, texte courant et libellés. */
val RobotoFlex = variableFamily(R.font.roboto_flex)

/** Style Google Sans Flex (taille, interligne en sp ; [tracking] en px de maquette). */
fun gs(size: Int, line: Int = size, weight: Int = 500, tracking: Float = 0f, tnum: Boolean = false) = TextStyle(
    fontFamily = GoogleSansFlex, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight),
    letterSpacing = (tracking / size).em, fontFeatureSettings = if (tnum) "tnum" else null,
)

/** Style Roboto Flex. */
fun rf(size: Number, line: Number = size.toFloat() * 1.4f, weight: Int = 400, tracking: Float = 0f, tnum: Boolean = false) = TextStyle(
    fontFamily = RobotoFlex, fontSize = size.toFloat().sp, lineHeight = line.toFloat().sp, fontWeight = FontWeight(weight),
    letterSpacing = (tracking / size.toFloat()).em, fontFeatureSettings = if (tnum) "tnum" else null,
)

val CdTypography = Typography(
    displayLarge = gs(57, 64, 400), displayMedium = gs(45, 52, 400), displaySmall = gs(36, 44, 400),
    headlineLarge = gs(32, 40, 500), headlineMedium = gs(28, 36, 500), headlineSmall = gs(24, 32, 500),
    titleLarge = gs(22, 28, 500), titleMedium = rf(16, 24, 600), titleSmall = rf(14, 20, 600),
    bodyLarge = rf(16, 24, 400), bodyMedium = rf(14, 20, 400), bodySmall = rf(12, 16, 400),
    labelLarge = rf(14, 20, 600), labelMedium = rf(12, 16, 600), labelSmall = rf(11, 16, 500),
)

val CdShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------- Thème

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

@Composable
fun CarDeckTheme(palette: Palette, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val ctx = LocalContext.current
    val scheme = when {
        palette == Palette.Dynamique && dynamicColorAvailable -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        palette == Palette.Neutre -> if (dark) NeutralDark else NeutralLight
        else -> if (dark) RoseDark else RoseLight
    }
    val c = cdColors(scheme, dark).animated()
    val view = LocalView.current
    val haptics = remember(view) { Haptics(view) }
    CompositionLocalProvider(
        LocalCd provides c,
        LocalAccent provides AccentRoles(c.p, c.op, c.pc, c.opc),
        LocalHaptics provides haptics,
    ) {
        MaterialTheme(colorScheme = scheme, typography = CdTypography, shapes = CdShapes) {
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.on, content = content)
        }
    }
}

/** Passage en douceur d'une palette à l'autre (thème clair / sombre, couleurs), 400 ms. */
@Composable
private fun CdColors.animated(): CdColors {
    val spec = tween<Color>(400, easing = Motion.Emphasized)
    @Composable fun Color.a(): Color = animateColorAsState(this, spec, label = "cd").value
    return copy(
        p = p.a(), op = op.a(), pc = pc.a(), opc = opc.a(), s = s.a(), os = os.a(), sc = sc.a(), osc = osc.a(),
        t = t.a(), ot = ot.a(), tc = tc.a(), otc = otc.a(),
        sf = sf.a(), sfl = sfl.a(), sf1 = sf1.a(), sf2 = sf2.a(), sf3 = sf3.a(), sf4 = sf4.a(),
        on = on.a(), onv = onv.a(), ol = ol.a(), olv = olv.a(), isf = isf.a(), ion = ion.a(), ip = ip.a(),
        e = e.a(), oe = oe.a(), ec = ec.a(), oec = oec.a(), w = w.a(), wc = wc.a(), owc = owc.a(), g = g.a(), gc = gc.a(), ogc = ogc.a(),
    )
}

/** Convertit une couleur OKLCH en sRGB (avec réduction de chroma si hors gamut, comme les navigateurs). */
fun oklch(l: Double, c: Double, h: Double): Color {
    fun toRgb(chroma: Double): DoubleArray {
        val hr = Math.toRadians(h)
        val a = chroma * cos(hr)
        val b = chroma * sin(hr)
        val l_ = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m_ = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s_ = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        return doubleArrayOf(
            4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
            -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
            -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_,
        )
    }
    fun inGamut(v: DoubleArray) = v.all { it in -0.0005..1.0005 }
    var rgb = toRgb(c)
    if (!inGamut(rgb)) {
        var lo = 0.0
        var hi = c
        repeat(18) {
            val mid = (lo + hi) / 2
            if (inGamut(toRgb(mid))) lo = mid else hi = mid
        }
        rgb = toRgb(lo)
    }
    fun enc(x: Double): Float {
        val v = x.coerceIn(0.0, 1.0)
        return (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).toFloat()
    }
    return Color(enc(rgb[0]), enc(rgb[1]), enc(rgb[2]))
}
