package com.cardeck.app.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cardeck.app.data.DashGauges
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.fr
import com.cardeck.app.service.TripService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Point d'entrée Android Auto : l'écran d'accueil de CarDeck sur l'écran de la voiture. */
class CarDeckCarService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(): Session = CarDeckSession()
}

private class CarDeckSession : Session() {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = CarMotion.stop(carContext)
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        // Le téléphone garde la liaison OBD2 ; l'écran de la voiture affiche les mêmes données.
        TripService.start(carContext)
        CarMotion.start(carContext)
        return HomeCarScreen(carContext)
    }
}

/** Accueil : mêmes cartes que sur le téléphone, dans le même ordre ; grisées (sauf le tableau de bord) quand le véhicule roule. */
internal class HomeCarScreen(ctx: CarContext) : CarScreen(ctx, parkedOnly = false) {
    private class Tile(val id: String, val label: String, val icon: ImageVector)

    private val tiles = listOf(
        Tile("dash", "Tableau de bord", Icons.Rounded.Dashboard),
        Tile("obd", "Données OBD2", Icons.Rounded.Memory),
        Tile("diag", "Diagnostic", Icons.Rounded.Troubleshoot),
        Tile("trips", "Trajets", Icons.Rounded.Route),
        Tile("coding", "Codage", Icons.Rounded.Build),
        Tile("add", "Ajouter un véhicule", Icons.Rounded.AddCircle),
        Tile("pair", "Boîtier OBD2", Icons.Rounded.SettingsInputHdmi),
        Tile("settings", "Paramètres", Icons.Rounded.Settings),
    )
    private var codes = 0
    private var moving = false

    override suspend fun snapshot(): Any? {
        val v = active
        codes = v?.let { withContext(Dispatchers.IO) { app.repo.lastScan(it.id) } }?.codes?.size ?: 0
        moving = CarMotion.moving(carContext)
        return listOf(v, codes, moving, vm.homeOrder, connLabel())
    }

    private fun ordered(): List<Tile> {
        val order = vm.homeOrder
        val known = tiles.map { it.id }
        val ids = order.filter { it in known }.distinct() + known.filter { it !in order }
        return ids.map { id -> tiles.first { it.id == id } }
    }

    /** Le tableau de bord reste disponible en roulant ; le reste demande l'arrêt du véhicule. */
    private fun enabled(t: Tile) = t.id == "dash" || !moving

    private fun text(t: Tile): String? = when {
        !enabled(t) -> "À l'arrêt uniquement"
        t.id == "diag" && codes > 0 -> "$codes défaut${if (codes > 1) "s" else ""}"
        t.id == "pair" -> connLabel()
        else -> null
    }

    private fun open(t: Tile) {
        if (!enabled(t)) { toast("Disponible uniquement à l'arrêt"); return }
        val v = active
        if (v == null && t.id != "add") { toast("Ajoutez d'abord un véhicule"); return }
        push(
            when (t.id) {
                "dash" -> DashCarScreen(carContext, vm.dashSport, fromDash = false)
                "obd" -> ObdCarScreen(carContext)
                "diag" -> DiagCarScreen(carContext)
                "trips" -> TripsCarScreen(carContext)
                "coding" -> CodingCarScreen(carContext)
                "add" -> AddVehicleCarScreen(carContext)
                "pair" -> PairCarScreen(carContext, v!!.id)
                else -> SettingsCarScreen(carContext)
            },
        )
    }

    override fun onGetTemplate(): Template {
        moving = CarMotion.moving(carContext)
        val all = ordered()
        val max = limit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        val items = ItemList.Builder().apply {
            all.take(max).forEach { t ->
                addItem(
                    GridItem.Builder()
                        .setTitle(t.label)
                        .apply { text(t)?.let { setText(it) } }
                        .setImage(CarIcons.of(t.icon, if (enabled(t)) CarIcons.GREEN else CarIcons.GREY), GridItem.IMAGE_TYPE_ICON)
                        .setOnClickListener { open(t) }
                        .build(),
                )
            }
        }.build()
        return GridTemplate.Builder()
            .setTitle(active?.nickname ?: "CarDeck")
            .setHeaderAction(Action.APP_ICON)
            .apply {
                // Écran trop petit pour toutes les cartes : les dernières passent en boutons, en haut à droite.
                val extra = all.drop(max).take(2)
                if (extra.isNotEmpty()) setActionStrip(ActionStrip.Builder().apply { extra.forEach { t -> addAction(action(t.label) { open(t) }) } }.build())
            }
            .setSingleList(items)
            .build()
    }
}

/** Libellés courts des données du tableau de bord sur l'écran de la voiture. */
private val SHORT_LABELS = mapOf(
    "dash_volt" to "Batterie",
    "dash_map" to "MAP",
    "dash_boost" to "Suralimentation",
    "std0E" to "Avance allumage",
)

/**
 * Tableau de bord en grille. Android Auto limite le nombre de nouveaux écrans : seules les valeurs (texte des tuiles)
 * changent d'une mise à jour à l'autre, les titres restent fixes pour que l'hôte les compte comme un simple rafraîchissement.
 * [fromDash] : ouvert depuis l'autre mode (le bouton Standard / Sport revient alors en arrière).
 */
internal class DashCarScreen(ctx: CarContext, private val sport: Boolean, private val fromDash: Boolean) : CarScreen(ctx, parkedOnly = false) {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = vm.updateDashSport(sport)
        })
    }

    override suspend fun whileVisible() {
        val ids = if (sport) DashGauges.SPORT_IDS else DashGauges.STANDARD_IDS
        app.obd.watch(slow = { it.id in DashGauges.SLOW_IDS }, withVolt = { !sport && 0x42 !in it }) { sup ->
            val tpl = vm.repo.active?.template
            DashGauges.defs(ids + if (sport) tpl?.dashSport.orEmpty() else emptyList(), sup, tpl?.pids.orEmpty())
        }
    }

    override suspend fun snapshot(): Any? = tiles().map { it.title to it.text }

    private class Tile(val title: String, val text: String, val icon: ImageVector)

    private fun tiles(): List<Tile> {
        val v = active
        val status = TripService.live.value ?: "Suivi inactif · ouvrez CarDeck sur le téléphone"
        val track = Tile("Suivi", if (v?.adapter == null) "Aucun boîtier associé" else status, Icons.Rounded.DirectionsCar)
        val values = if (connected) app.obd.live.value else emptyMap()
        val gauges = DashGauges.gauges(
            sport, values, app.obd.supported.value, v?.template?.pids.orEmpty().map { it.id }.toSet(),
            if (sport) v?.template?.let { t -> t.pids.filter { it.id in t.dashSport } }.orEmpty() else emptyList(),
        ).map { g ->
            val text = when {
                g.value != null -> g.def.withUnit(g.value)
                g.note == DashGauges.MISSING -> "Non fourni"
                else -> "—"
            }
            Tile(SHORT_LABELS[g.def.id] ?: g.def.label, text, g.def.icon)
        }
        return gauges + track
    }

    override fun onGetTemplate(): Template {
        val list = tiles().take(limit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID))
        val items = ItemList.Builder().apply {
            list.forEach { t -> addItem(GridItem.Builder().setTitle(t.title).setText(t.text).setImage(CarIcons.of(t.icon), GridItem.IMAGE_TYPE_ICON).build()) }
        }.build()
        val switch = action(if (sport) "Standard" else "Sport") {
            if (fromDash) screenManager.pop() else push(DashCarScreen(carContext, !sport, fromDash = true))
        }
        val name = active?.nickname ?: "CarDeck"
        return GridTemplate.Builder()
            .setTitle(if (sport) "$name · Sport" else name)
            .setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(switch).build())
            .setSingleList(items)
            .build()
    }
}
