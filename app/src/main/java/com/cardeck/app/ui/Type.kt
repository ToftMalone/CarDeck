package com.cardeck.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardeck.app.R

private val weights = listOf(300, 400, 500, 600, 700, 800)

/** Polices variables embarquées : seul l'axe de graisse (et la taille optique de Google Sans Flex) est conservé. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

/** Google Sans Flex : display, headline, titleLarge et métriques en direct. */
val GoogleSansFlex = variable(R.font.google_sans_flex)

/** Roboto Flex : titres moyens, corps de texte et libellés. */
val RobotoFlex = variable(R.font.roboto_flex)

/** Chiffres tabulaires : les nombres qui changent ne font pas bouger le texte. */
const val TNUM = "tnum"

private fun gsf(size: Int, line: Int, w: Int = 400, ls: Double = 0.0) =
    TextStyle(fontFamily = GoogleSansFlex, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(w), letterSpacing = ls.sp)

private fun rf(size: Int, line: Int, w: Int = 400, ls: Double = 0.0) =
    TextStyle(fontFamily = RobotoFlex, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(w), letterSpacing = ls.sp)

val CarDeckTypography = Typography(
    displayLarge = gsf(57, 64, ls = -0.25),
    displayMedium = gsf(45, 52),
    displaySmall = gsf(36, 44),
    headlineLarge = gsf(32, 40),
    headlineMedium = gsf(28, 36),
    headlineSmall = gsf(24, 32),
    titleLarge = gsf(22, 28, 500),
    titleMedium = rf(16, 24, 600, 0.15),
    titleSmall = rf(14, 20, 500, 0.1),
    bodyLarge = rf(16, 24, ls = 0.5),
    bodyMedium = rf(14, 20, ls = 0.25),
    bodySmall = rf(12, 16, ls = 0.4),
    labelLarge = rf(14, 20, 500, 0.1),
    labelMedium = rf(12, 16, 500, 0.5),
    labelSmall = rf(11, 16, 500, 0.5),
)

/** Styles des métriques en direct (Google Sans Flex, chiffres tabulaires). */
object MetricStyle {
    val hero = gsf(72, 76, 500, -1.0).copy(fontFeatureSettings = TNUM)
    val large = gsf(45, 52, 500).copy(fontFeatureSettings = TNUM)
    val medium = gsf(28, 34, 500).copy(fontFeatureSettings = TNUM)
    val small = gsf(20, 26, 500).copy(fontFeatureSettings = TNUM)
}

/** Échelle des rayons : 4 / 8 / 12 / 20 / 24 / 28 / 32 dp. */
object Radius {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 20.dp
    val section = 24.dp
    val xl = 28.dp
    val hero = 32.dp
}

object Corners {
    val hero = RoundedCornerShape(Radius.hero)
    val section = RoundedCornerShape(Radius.section)
    val primaryButton = RoundedCornerShape(Radius.xl)
    val secondary = RoundedCornerShape(Radius.l)
    val chip = RoundedCornerShape(Radius.l)
    val dock = RoundedCornerShape(Radius.xl)
    val small = RoundedCornerShape(Radius.m)
}

val CarDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
    extraLarge = RoundedCornerShape(Radius.xl),
)
