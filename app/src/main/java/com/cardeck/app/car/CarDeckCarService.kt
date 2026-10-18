package com.cardeck.app.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.car.app.versioning.CarAppApiLevels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cardeck.app.cardeck
import com.cardeck.app.data.DashGauges
import com.cardeck.app.data.Prefs
import com.cardeck.app.data.fr
import com.cardeck.app.obd.ObdState
import com.cardeck.app.service.TripService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Point d'entrée Android Auto : tableau de bord Standard / Sport sur l'écran de la voiture. */
class CarDeckCarService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(): Session = DashSession()
}

private class DashSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        // Le téléphone garde la liaison OBD2 ; l'écran de la voiture affiche les mêmes données.
        TripService.start(carContext)
        val standard = DashCarScreen(carContext, sport = false)
        if (Prefs(carContext).dashMode != "sport") return standard
        carContext.getCarService(ScreenManager::class.java).push(standard)
        return DashCarScreen(carContext, sport = true)
    }
}

/**
 * Tableau de bord en grille. Android Auto limite le nombre de nouveaux écrans : seules les valeurs (texte des tuiles)
 * changent d'une mise à jour à l'autre, les titres restent fixes pour que l'hôte les compte comme un simple rafraîchissement.
 */
private class DashCarScreen(ctx: CarContext, private val sport: Boolean) : Screen(ctx) {
    private val app = ctx.cardeck
    private val icons = HashMap<String, CarIcon>()
    private var shown = emptyList<Pair<String, String>>()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                Prefs(carContext).dashMode = if (sport) "sport" else "standard"
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(REFRESH_MS)
                    if (tiles().map { it.title to it.text } != shown) invalidate()
                }
            }
        }
    }

    private class Tile(val id: String, val title: String, val text: String, val icon: ImageVector)

    private fun tiles(): List<Tile> {
        val v = app.repo.active
        val connected = app.obd.state.value is ObdState.Connected
        val status = TripService.live.value ?: "Suivi inactif · ouvrez CarDeck sur le téléphone"
        val track = Tile("status", "Suivi", if (v?.adapter == null) "Aucun boîtier associé" else status, Icons.Rounded.DirectionsCar)
        if (v == null) return listOf(Tile("status", "Suivi", "Ajoutez un véhicule dans CarDeck sur le téléphone", Icons.Rounded.DirectionsCar))
        val values = if (connected) app.obd.live.value else emptyMap()
        val gauges = DashGauges.gauges(sport, values, app.obd.supported.value).map { g ->
            val text = when {
                g.value != null -> "${fr(g.value, g.def.decimals)} ${g.def.unit}"
                g.note == DashGauges.MISSING -> "Non fourni"
                else -> "—"
            }
            Tile(g.def.id, g.def.label, text, g.def.icon)
        }
        return gauges + track
    }

    override fun onGetTemplate(): Template {
        val list = tiles().take(gridLimit())
        shown = list.map { it.title to it.text }
        val items = ItemList.Builder().apply {
            list.forEach { t ->
                addItem(GridItem.Builder().setTitle(t.title).setText(t.text).setImage(icon(t.id, t.icon), GridItem.IMAGE_TYPE_ICON).build())
            }
        }.build()
        val switch = Action.Builder()
            .setTitle(if (sport) "Standard" else "Sport")
            .setOnClickListener {
                val sm = screenManager
                if (sport) sm.pop() else sm.push(DashCarScreen(carContext, sport = true))
            }
            .build()
        val name = app.repo.active?.nickname ?: "CarDeck"
        return GridTemplate.Builder()
            .setTitle(if (sport) "$name · Sport" else name)
            .setHeaderAction(if (sport) Action.BACK else Action.APP_ICON)
            .setActionStrip(ActionStrip.Builder().addAction(switch).build())
            .setSingleList(items)
            .build()
    }

    private fun gridLimit(): Int =
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            runCatching { carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID) }.getOrDefault(6)
        } else 6

    private fun icon(id: String, v: ImageVector): CarIcon = icons.getOrPut(id) {
        CarIcon.Builder(IconCompat.createWithBitmap(v.toBitmap(ICON_PX)))
            .setTint(CarColor.createCustom(GREEN_LIGHT, GREEN_DARK))
            .build()
    }

    companion object {
        private const val REFRESH_MS = 1000L
        private const val ICON_PX = 128
        private const val GREEN_LIGHT = 0xFF0E9F6E.toInt()
        private const val GREEN_DARK = 0xFF3DDC97.toInt()
    }
}

/** Dessine une icône Material (ImageVector) dans un bitmap blanc, teinté ensuite par l'hôte. */
private fun ImageVector.toBitmap(px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(px / viewportWidth, px / viewportHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    fun draw(g: VectorGroup) {
        for (n in g) when (n) {
            is VectorPath -> {
                val path = PathParser().addPathNodes(n.pathData).toPath().asAndroidPath()
                path.fillType = if (n.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                canvas.drawPath(path, paint)
            }
            is VectorGroup -> draw(n)
        }
    }
    draw(root)
    return bmp
}
