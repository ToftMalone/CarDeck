package com.cardeck.app.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.cardeck.app.data.DtcCatalog
import com.cardeck.app.data.DtcScan
import com.cardeck.app.data.FoundCode
import com.cardeck.app.data.agoLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diagnostic : dernier relevé des codes défaut, lancement d'un nouveau diagnostic, effacement, recherche. */
internal class DiagCarScreen(ctx: CarContext) : CarScreen(ctx) {
    private var scan: DtcScan? = null
    private var loaded = false

    override suspend fun snapshot(): Any? {
        val v = active ?: return null
        scan = withContext(Dispatchers.IO) { app.repo.lastScan(v.id) }
        loaded = true
        return listOf(scan?.id, vm.scanning, vm.scanStep, connected)
    }

    private fun start() {
        if (!connected) { toast("Boîtier OBD2 non connecté"); return }
        vm.startScan()
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val v = active ?: return message("Diagnostic", "Aucun véhicule.")
        val b = ListTemplate.Builder().setTitle("Diagnostic").setHeaderAction(Action.BACK)
        // Pendant la lecture (et avant le premier chargement), un écran d'attente : la liste suivante n'est qu'un rafraîchissement.
        if (!loaded || vm.scanning) return b.setLoading(true).build()
        val s = scan
        val items = ItemList.Builder()
        if (s == null) {
            items.addItem(Row.Builder().setTitle("Aucun diagnostic").addText("Mettez le contact puis lancez un diagnostic de ${v.nickname}.").build())
        } else {
            val n = s.codes.size
            items.addItem(
                Row.Builder().setTitle(if (n == 0) "Aucun défaut" else "$n défaut${if (n > 1) "s" else ""}")
                    .addText("Diagnostic ${agoLabel(s.t)} · ${s.ecus} calculateur${if (s.ecus > 1) "s" else ""}").build(),
            )
            s.codes.forEach { c ->
                val info = DtcCatalog.info(c.code)
                items.addItem(
                    Row.Builder().setTitle("${c.code} · ${info.title}").addText("${c.status.label} · ${info.sev.label} · ${info.sys.label}")
                        .setBrowsable(true).setOnClickListener { push(DtcCarScreen(carContext, c.code, c)) }.build(),
                )
            }
            val ff = s.ff
            if (!ff.isNullOrEmpty()) {
                items.addItem(
                    Row.Builder().setTitle("Données figées" + (s.ffCode?.let { " · $it" } ?: "")).addText("Valeurs relevées au moment du défaut")
                        .setBrowsable(true).setOnClickListener {
                            push(TextCarScreen(carContext, "Données figées" + (s.ffCode?.let { " · $it" } ?: ""), ff.joinToString("\n")))
                        }.build(),
                )
            }
        }
        items.addItem(
            Row.Builder().setTitle("Rechercher un code défaut").addText("Base de codes OBD2 en français")
                .setBrowsable(true).setOnClickListener { push(DtcSearchCarScreen(carContext)) }.build(),
        )
        val strip = ActionStrip.Builder().addAction(action(if (s == null) "Lancer" else "Relancer") { start() })
        if ((s?.codes?.size ?: 0) > 0) {
            strip.addAction(
                action("Effacer") {
                    if (!connected) { toast("Boîtier OBD2 non connecté"); return@action }
                    push(
                        ConfirmCarScreen(
                            carContext, "Effacer les codes défaut ?",
                            "Les codes mémorisés et le voyant moteur seront effacés. Mettez le contact, moteur arrêté. " +
                                "Si le problème persiste, les codes réapparaîtront.",
                            "Effacer", danger = true,
                        ) { c ->
                            vm.clearFaults()
                            c.screenManager.pop()
                        },
                    )
                },
            )
        }
        return b.setActionStrip(strip.build()).setSingleList(items.build()).build()
    }
}

/** Fiche d'un code défaut : description, causes et recommandations. */
internal class DtcCarScreen(ctx: CarContext, private val code: String, private val found: FoundCode? = null) : CarScreen(ctx) {
    override fun onGetTemplate(): Template {
        val d = DtcCatalog.info(code)
        val text = buildString {
            append("${d.sys.label} · Gravité : ${d.sev.label}")
            found?.let { append("\n${it.status.label} · Calculateur ${it.ecu}") }
            d.desc?.let { append("\n\n$it") }
            if (d.causes.isNotEmpty()) append("\n\nCauses possibles :\n" + d.causes.joinToString("\n") { "• $it" })
            if (d.recos.isNotEmpty()) append("\n\nRecommandations :\n" + d.recos.joinToString("\n") { "• $it" })
        }
        return LongMessageTemplate.Builder(text).setTitle("$code · ${d.title}").setHeaderAction(Action.BACK).build()
    }
}

/** Texte long avec retour (données figées…). */
internal class TextCarScreen(ctx: CarContext, private val title: String, private val text: String) : CarScreen(ctx) {
    override fun onGetTemplate(): Template = LongMessageTemplate.Builder(text).setTitle(title).setHeaderAction(Action.BACK).build()
}

/** Recherche dans la base des codes défaut (clavier disponible à l'arrêt). */
internal class DtcSearchCarScreen(ctx: CarContext) : CarScreen(ctx) {
    private var query = ""

    override fun onGetTemplate(): Template {
        val q = query.trim().lowercase()
        val res = if (q.isEmpty()) emptyList() else DtcCatalog.all.filter { (it.code + " " + it.title + " " + it.sys.label).lowercase().contains(q) }
            .ifEmpty { if (Regex("[pcbu][0-9a-f]{4}").matches(q)) listOf(DtcCatalog.info(q.uppercase())) else emptyList() }
        val items = ItemList.Builder().setNoItemsMessage(if (q.isEmpty()) "Saisissez un code (ex. P0301) ou un mot-clé" else "Aucun code trouvé")
        res.forEach { d ->
            items.addItem(
                Row.Builder().setTitle("${d.code} · ${d.title}").addText("${d.sys.label} · ${d.sev.label}")
                    .setBrowsable(true).setOnClickListener { push(DtcCarScreen(carContext, d.code)) }.build(),
            )
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) { query = searchText; invalidate() }
            override fun onSearchSubmitted(searchText: String) { query = searchText; invalidate() }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Code ou mot-clé")
            .setInitialSearchText(query)
            .setShowKeyboardByDefault(true)
            .setItemList(items.build())
            .build()
    }
}
