package com.cardeck.app.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.cardeck.app.data.EventType
import com.cardeck.app.data.Trip
import com.cardeck.app.data.dayLabel
import com.cardeck.app.data.durationLabel
import com.cardeck.app.data.fr
import com.cardeck.app.data.timeHm
import com.cardeck.app.screens.TRIP_FILTERS
import com.cardeck.app.screens.tripInPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TRIPS_MARKER = "trips"

/** Historique des trajets du véhicule actif, avec le même filtre de période que sur le téléphone. */
internal class TripsCarScreen(ctx: CarContext) : CarScreen(ctx) {
    private var all: List<Trip>? = null

    init { marker = TRIPS_MARKER }

    override suspend fun snapshot(): Any? {
        val v = active ?: return null
        all = withContext(Dispatchers.IO) { app.repo.trips(v.id) }
        return listOf(all?.map { it.id }, vm.tripFilter)
    }

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle("Trajets").setHeaderAction(Action.BACK)
        val loadedTrips = all ?: return b.setLoading(true).build()
        val tf = vm.tripFilter
        val now = System.currentTimeMillis()
        val list = loadedTrips.filter { tripInPeriod(it, tf, now) }
        val km = list.sumOf { it.distanceKm }
        val items = ItemList.Builder().setNoItemsMessage(
            if (loadedTrips.isEmpty()) "Aucun trajet enregistré pour l'instant. Les trajets sont enregistrés automatiquement dès que le moteur démarre avec le boîtier branché."
            else "Aucun trajet sur cette période",
        )
        list.forEach { t ->
            items.addItem(
                Row.Builder().setTitle("${dayLabel(t.start)} · ${timeHm(t.start)} – ${timeHm(t.end)}")
                    .addText("${fr(t.distanceKm, 1)} km · ${durationLabel(t.end - t.start)} · ${t.from} → ${t.to}")
                    .setBrowsable(true).setOnClickListener { push(TripCarScreen(carContext, t.id)) }.build(),
            )
        }
        val label = TRIP_FILTERS.first { it.first == tf }.second
        val next = TRIP_FILTERS[(TRIP_FILTERS.indexOfFirst { it.first == tf } + 1) % TRIP_FILTERS.size].first
        return b
            .setTitle(if (list.isEmpty()) "Trajets" else "Trajets · ${list.size} · ${fr(km, 1)} km")
            .setActionStrip(
                ActionStrip.Builder().addAction(
                    action("Période : $label") {
                        vm.tripFilter = next
                        // Écran d'attente le temps de recharger : la nouvelle liste compte comme un rafraîchissement.
                        all = null
                        invalidate()
                    },
                ).build(),
            )
            .setSingleList(items.build())
            .build()
    }
}

/** Résumé d'un trajet (la carte et le replay restent sur le téléphone). */
internal class TripCarScreen(ctx: CarContext, private val id: Long) : CarScreen(ctx) {
    private var trip: Trip? = null
    private var loaded = false

    override suspend fun snapshot(): Any? {
        trip = withContext(Dispatchers.IO) { app.repo.trip(id) }
        loaded = true
        return trip
    }

    override fun onGetTemplate(): Template {
        if (!loaded) return PaneTemplate.Builder(Pane.Builder().setLoading(true).build()).setTitle("Trajet").setHeaderAction(Action.BACK).build()
        val t = trip ?: return message("Trajet", "Ce trajet n'existe plus.")
        fun count(type: EventType) = t.events.count { it.type == type }
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Départ · ${timeHm(t.start)}").addText(t.from).build())
            .addRow(Row.Builder().setTitle("Arrivée · ${timeHm(t.end)}").addText(t.to).build())
            .addRow(
                Row.Builder().setTitle("${fr(t.distanceKm, 1)} km · ${durationLabel(t.end - t.start)}")
                    .addText("Vitesse moyenne ${fr(t.avgKmh)} km/h · max ${fr(t.maxKmh)} km/h").build(),
            )
            .addRow(
                Row.Builder().setTitle(if (t.events.isEmpty()) "Aucun événement" else "${t.events.size} événement${if (t.events.size > 1) "s" else ""}")
                    .addText(EventType.entries.joinToString(" · ") { "${it.label} : ${count(it)}" }).build(),
            )
            .addAction(
                action("Supprimer") {
                    push(
                        ConfirmCarScreen(carContext, "Supprimer ce trajet ?", "Le trajet et toutes ses données seront définitivement supprimés.", "Supprimer", danger = true) { c ->
                            app.repo.deleteTrip(id)
                            c.screenManager.popTo(TRIPS_MARKER)
                        },
                    )
                },
            )
            .build()
        return PaneTemplate.Builder(pane).setTitle("${dayLabel(t.start)} · ${timeHm(t.start)}").setHeaderAction(Action.BACK).build()
    }
}
