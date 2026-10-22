package com.cardeck.app.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.cardeck.app.data.CodingBackup
import com.cardeck.app.data.CodingDef
import com.cardeck.app.data.dayLabel
import com.cardeck.app.data.timeHm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Codage (comme sur le téléphone) : lecture du codage actuel, choix des nouvelles valeurs, écriture, restauration. */
internal class CodingCarScreen(ctx: CarContext) : CarScreen(ctx) {
    private var backups: List<CodingBackup>? = null

    init { active?.let { vm.codingFor(it.id) } }

    override suspend fun snapshot(): Any? {
        val v = active ?: return null
        backups = withContext(Dispatchers.IO) { app.repo.codingBackups(v.id) }
        return listOf(v.id, vm.codingBusy, vm.codingData.keys, vm.codingErrors, vm.codingPending, vm.codingReport, backups?.size, connected)
    }

    private fun valueLabel(d: CodingDef, value: Int?): String =
        d.options.find { it.value == value }?.label ?: value?.let { "0x%02X (non répertoriée)".format(it) } ?: "—"

    override fun onGetTemplate(): Template {
        val v = active ?: return message("Codage", "Aucun véhicule.")
        val tpl = v.template
        if (tpl.codings.isEmpty()) {
            return message(
                "Codage",
                tpl.codingImpossible?.let { "Le codage n'est pas possible. $it" }
                    ?: "Fonctions de codage à venir : les fonctions de codage de la ${tpl.fullName} seront ajoutées avec les données du modèle (calculateurs, identifiants et valeurs).",
            )
        }
        val b = ListTemplate.Builder().setTitle("Codage").setHeaderAction(Action.BACK)
        // Lecture / écriture en cours : écran d'attente (ne coupez pas le contact).
        if (vm.codingBusy != null || backups == null) return b.setLoading(true).build()
        val items = ItemList.Builder()
        // Titre fixe (« État ») : seul le texte change, Android Auto compte un simple rafraîchissement.
        val state = vm.codingReport?.let { (ok, msg) -> (if (ok) "Codage appliqué" else "Échec") to msg } ?: when {
            !connected -> "Boîtier non connecté" to "Connectez le boîtier OBD2 pour lire le codage."
            vm.codingData.isEmpty() -> "Codage non lu" to "Mettez le contact (moteur arrêté) puis lisez le codage actuel de la voiture."
            else -> "Codage lu" to "Choisissez les nouvelles valeurs puis appliquez."
        }
        items.addItem(Row.Builder().setTitle("État").addText(state.first).addText(state.second).build())
        tpl.codings.forEach { d ->
            val data = vm.codingData[d.blockKey]
            val error = vm.codingErrors[d.blockKey]
            val current = data?.let { d.valueIn(it) }
            val pending = vm.codingPending[d.id]
            val text = when {
                error != null -> error
                data == null -> "Valeur actuelle : non lue"
                pending != null -> "${valueLabel(d, current)} → ${valueLabel(d, pending)}"
                else -> "Actuel : ${valueLabel(d, current)}"
            }
            items.addItem(
                Row.Builder().setTitle(d.label).addText(text).addText("${d.category} · ${d.module}")
                    .setBrowsable(true)
                    .setOnClickListener {
                        if (data == null) toast("Lisez d'abord le codage") else push(CodingOptionsCarScreen(carContext, d, current))
                    }.build(),
            )
        }
        val saved = backups.orEmpty()
        if (saved.isNotEmpty()) {
            items.addItem(
                Row.Builder().setTitle("Restaurer le codage d'origine")
                    .addText("Sauvegardé avant la première modification : " + saved.joinToString(", ") { "${it.module} (${dayLabel(it.t)} ${timeHm(it.t)})" })
                    .setBrowsable(true)
                    .setOnClickListener {
                        if (!connected) { toast("Boîtier OBD2 non connecté"); return@setOnClickListener }
                        push(
                            ConfirmCarScreen(
                                carContext, "Restaurer le codage d'origine ?",
                                "Toutes les modifications de codage faites avec CarDeck sur ce véhicule seront annulées.", "Restaurer",
                            ) { c -> vm.restoreCoding(); c.screenManager.pop() },
                        )
                    }.build(),
            )
        }
        val strip = ActionStrip.Builder().addAction(
            action(if (vm.codingData.isEmpty()) "Lire" else "Relire") {
                if (connected) vm.readCoding() else toast("Boîtier OBD2 non connecté")
                invalidate()
            },
        )
        val n = vm.codingPending.size
        if (n > 0) {
            strip.addAction(
                action("Appliquer ($n)") {
                    if (!connected) { toast("Boîtier OBD2 non connecté"); return@action }
                    val modules = tpl.codings.filter { it.id in vm.codingPending }.map { it.module }.distinct().joinToString(", ")
                    push(
                        ConfirmCarScreen(
                            carContext, "Écrire le codage ?",
                            "Modules concernés : $modules. Le codage d'origine est sauvegardé automatiquement et pourra être restauré. " +
                                "Contact mis, moteur arrêté, batterie chargée ; ne coupez pas le contact pendant l'écriture.",
                            "Écrire",
                        ) { c -> vm.applyCoding(); c.screenManager.pop() },
                    )
                },
            )
        }
        return b.setActionStrip(strip.build()).setSingleList(items.build()).build()
    }
}

/** Choix de la valeur d'un réglage (la valeur actuelle est indiquée). */
internal class CodingOptionsCarScreen(ctx: CarContext, private val d: CodingDef, private val current: Int?) : CarScreen(ctx) {
    override fun onGetTemplate(): Template {
        val chosen = vm.codingPending[d.id] ?: current
        val items = ItemList.Builder()
        d.options.forEach { o -> items.addItem(Row.Builder().setTitle(o.label + if (o.value == current) " (actuel)" else "").build()) }
        d.options.indexOfFirst { it.value == chosen }.takeIf { it >= 0 }?.let { items.setSelectedIndex(it) }
        items.setOnSelectedListener { i ->
            if (vm.codingBusy == null) vm.choose(d.id, d.options[i].value, current)
            screenManager.pop()
        }
        return ListTemplate.Builder().setTitle(d.label).setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}
