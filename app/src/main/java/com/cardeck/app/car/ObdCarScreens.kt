package com.cardeck.app.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.fr
import com.cardeck.app.obd.ObdState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Données OBD2 : liaison avec le boîtier, identification, état sécurité, puis toutes les valeurs du véhicule par catégorie. */
internal class ObdCarScreen(ctx: CarContext) : CarScreen(ctx) {
    override suspend fun whileVisible() {
        // Liste des données fournies par le véhicule, nécessaire pour composer les catégories.
        while (true) {
            if (connected && app.obd.supported.value == null) {
                withContext(Dispatchers.IO) { app.obd.use { it.supportedPids() } }?.let { app.obd.supported.value = it }
            }
            delay(2000)
        }
    }

    override suspend fun snapshot(): Any? = listOf(active, connLabel(), app.obd.supported.value)

    override fun onGetTemplate(): Template {
        val v = active ?: return message("Données OBD2", "Aucun véhicule.")
        val items = ItemList.Builder()
        val a = v.adapter
        items.addItem(
            Row.Builder().setTitle("Liaison OBD2").addText(a?.let { "${connLabel(v)} · ${it.name} (${it.transport.label})" } ?: "Aucun boîtier associé")
                .setBrowsable(true).setOnClickListener { push(ObdLinkCarScreen(carContext)) }.build(),
        )
        if (v.template.idents.isNotEmpty()) {
            items.addItem(
                Row.Builder().setTitle("Identification").addText("VIN, kilométrage, références des calculateurs")
                    .setBrowsable(true).setOnClickListener { push(IdentCarScreen(carContext)) }.build(),
            )
        }
        if (v.template.airbag != null) {
            items.addItem(
                Row.Builder().setTitle("État sécurité (airbags)").addText("Circuits d'allumage, estimé")
                    .setBrowsable(true).setOnClickListener { push(SafetyCarScreen(carContext)) }.build(),
            )
        }
        // Toujours les mêmes lignes (une par catégorie) : Android Auto ne compte alors qu'un rafraîchissement.
        val sup = app.obd.supported.value
        val std = if (connected && sup != null) StdPids.forSupported(sup) else emptyList()
        val sections = StdPids.CATEGORIES.map { cat -> cat to std.filter { it.category == cat } } +
            v.template.pids.groupBy { it.category }.map { (cat, list) -> "$cat · ${v.template.model}" to if (connected) list else emptyList() }
        sections.forEach { (cat, defs) ->
            val row = Row.Builder().setTitle(cat)
            when {
                !connected -> row.addText("Boîtier non connecté")
                sup == null -> row.addText("Lecture des données disponibles… (contact mis)")
                defs.isEmpty() -> row.addText("Non fourni par le véhicule")
                else -> row.addText("${defs.size} valeur${if (defs.size > 1) "s" else ""}").setBrowsable(true)
                    .setOnClickListener { push(ObdValuesCarScreen(carContext, cat, defs)) }
            }
            items.addItem(row.build())
        }
        return ListTemplate.Builder().setTitle("Données OBD2").setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

/** Détails de la liaison avec le boîtier (même contenu que la carte « Liaison OBD2 » du téléphone). */
internal class ObdLinkCarScreen(ctx: CarContext) : CarScreen(ctx) {
    override suspend fun whileVisible() {
        while (true) {
            if (connected) withContext(Dispatchers.IO) { app.obd.use { it.voltage() } }?.let { app.obd.voltage.value = it }
            delay(5000)
        }
    }

    override suspend fun snapshot(): Any? = listOf(active, app.obd.state.value, app.obd.ecuOnline.value, app.obd.voltage.value, app.obd.protocol.value)

    override fun onGetTemplate(): Template {
        val v = active ?: return message("Liaison OBD2", "Aucun véhicule.")
        val a = v.adapter ?: return message(
            "Liaison OBD2", "Aucun boîtier n'est associé à ce véhicule.",
            action("Associer un boîtier", primary = true) { push(PairCarScreen(carContext, v.id)) },
        )
        val st = app.obd.state.value
        val on = connected
        fun row(t: String, s: String) = Row.Builder().setTitle(t).addText(s).build()
        val err = (st as? ObdState.Failed)?.takeIf { it.adapter.address == a.address }?.message
        val items = ItemList.Builder()
            .addItem(row("État", listOfNotNull(connLabel(v), err).joinToString(" · ")))
            .addItem(row("Boîtier", "${a.name} · ${a.transport.label}"))
            .addItem(row("Interface", (st as? ObdState.Connected)?.version ?: "—"))
            .addItem(row("Protocole", if (on) app.obd.protocol.value ?: "—" else "—"))
            .addItem(row("Véhicule", if (!on) "—" else if (app.obd.ecuOnline.value) "Calculateur moteur joignable" else "Contact coupé"))
            .addItem(row("Tension batterie", if (on) app.obd.voltage.value?.let { fr(it, 1) + " V" } ?: "—" else "—"))
            .addItem(row("VIN", v.vin ?: "Non lu"))
        return ListTemplate.Builder()
            .setTitle("Liaison OBD2")
            .setHeaderAction(Action.BACK)
            .apply { if (!on && st !is ObdState.Connecting) setActionStrip(ActionStrip.Builder().addAction(action("Se connecter") { vm.connectNow() }).build()) }
            .setSingleList(items.build())
            .build()
    }
}

/** Valeurs d'une catégorie, lues en continu tant que l'écran est affiché. */
internal class ObdValuesCarScreen(ctx: CarContext, private val title: String, private val defs: List<PidDef>) : CarScreen(ctx) {
    override suspend fun whileVisible() = app.obd.watch { defs }

    override suspend fun snapshot(): Any? = defs.map { app.obd.live.value[it.id] }

    override fun onGetTemplate(): Template {
        val values = if (connected) app.obd.live.value else emptyMap()
        val items = ItemList.Builder()
        defs.forEach { p ->
            items.addItem(
                Row.Builder().setTitle(p.label)
                    .addText(values[p.id]?.let { p.withUnit(it) } ?: "—")
                    .setImage(CarIcons.of(p.icon)).build(),
            )
        }
        return ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

/** Identification du véhicule : VIN, kilométrage, références et logiciels des calculateurs. */
internal class IdentCarScreen(ctx: CarContext) : CarScreen(ctx) {
    override suspend fun snapshot(): Any? = listOf(vm.identBusy, vm.ident, app.obd.live.value["swift_odo"])

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle("Identification").setHeaderAction(Action.BACK)
        if (vm.identBusy) return b.setLoading(true).build()
        val items = ItemList.Builder()
        items.addItem(Row.Builder().setTitle("VIN").addText(active?.vin ?: "Non lu").build())
        app.obd.live.value["swift_odo"]?.let { items.addItem(Row.Builder().setTitle("Kilométrage").addText(fr(it) + " km").build()) }
        vm.ident?.forEach { (ecu, lines) ->
            items.addItem(Row.Builder().setTitle(ecu).addText(if (lines.isEmpty()) "Pas de réponse" else lines.filter { !it.startsWith("VIN") }.joinToString(" · ")).build())
        }
        if (vm.ident == null) items.addItem(Row.Builder().setTitle("Calculateurs").addText("Appuyez sur « Lire », contact mis").build())
        return b.setActionStrip(
            ActionStrip.Builder().addAction(action("Lire") { if (connected) vm.readIdent() else toast("Boîtier OBD2 non connecté"); invalidate() }).build(),
        ).setSingleList(items.build()).build()
    }
}

/** État sécurité : circuits d'allumage des airbags (estimé), temps depuis la mise du contact. */
internal class SafetyCarScreen(ctx: CarContext) : CarScreen(ctx) {
    override suspend fun snapshot(): Any? = listOf(vm.safetyBusy, vm.safety)

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle("État sécurité").setHeaderAction(Action.BACK)
        if (vm.safetyBusy) return b.setLoading(true).build()
        val items = ItemList.Builder()
        val r = vm.safety
        if (r == null) {
            items.addItem(Row.Builder().setTitle("Airbags").addText("Appuyez sur « Lire », contact mis").build())
        } else {
            val normal = com.cardeck.app.data.AirbagReport.NORMAL
            val bad = r.circuits.count { it !in normal }
            items.addItem(
                Row.Builder().setTitle("Bilan (estimé)")
                    .addText(if (r.circuits.isEmpty()) "Pas de mesure" else if (bad == 0) "${r.circuits.size} circuits dans la plage normale" else "$bad circuit(s) hors plage").build(),
            )
            items.addItem(Row.Builder().setTitle("Circuits (Ω, estimé)").addText(r.circuits.joinToString(" · ") { fr(it, 1) }).build())
            r.secondsOn?.let { items.addItem(Row.Builder().setTitle("Contact mis depuis").addText("$it s").build()) }
        }
        return b.setActionStrip(
            ActionStrip.Builder().addAction(action("Lire") { if (connected) vm.readSafety() else toast("Boîtier OBD2 non connecté"); invalidate() }).build(),
        ).setSingleList(items.build()).build()
    }
}
