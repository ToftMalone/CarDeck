package com.cardeck.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.CATALOG
import com.cardeck.app.data.CatalogBrand
import com.cardeck.app.data.CatalogGeneration
import com.cardeck.app.data.CatalogModel
import com.cardeck.app.data.OTHER
import com.cardeck.app.data.VehicleTemplate
import com.cardeck.app.data.searchCatalog
import com.cardeck.app.data.templateById
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.Field
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn

/** Niveau affiché dans le sélecteur : marques, modèles d'une marque, versions d'un modèle. */
private enum class Level { Brands, Models, Variants }

/**
 * Création d'un profil : choix du véhicule dans le catalogue (Marque › Modèle › Génération et motorisation, ou recherche),
 * puis nom, immatriculation et année.
 */
@Composable
fun AddVehicleScreen(vm: AppViewModel) {
    var brandName by rememberSaveable { mutableStateOf<String?>(null) }
    var modelName by rememberSaveable { mutableStateOf<String?>(null) }
    var tplId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val tpl = tplId?.let { templateById(it) }
    val brand = CATALOG.find { it.name == brandName }
    val model = brand?.models?.find { it.name == modelName }

    // Retour : on remonte d'un niveau avant de quitter l'écran.
    BackHandler(enabled = tpl != null || brand != null || query.isNotEmpty()) {
        when {
            tpl != null -> tplId = null
            query.isNotEmpty() -> query = ""
            model != null -> modelName = null
            else -> brandName = null
        }
    }

    if (tpl != null) {
        VehicleDetails(vm, tpl, onChange = { tplId = null }, onBack = { tplId = null })
        return
    }

    val c = Cd.c
    val level = when {
        model != null -> Level.Variants
        brand != null -> Level.Models
        else -> Level.Brands
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, {
                when {
                    query.isNotEmpty() -> query = ""
                    model != null -> modelName = null
                    brand != null -> brandName = null
                    else -> vm.back()
                }
            })
            if (!vm.onboarded) T("Étape 1 sur 2", 14, c.onv, 500)
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp)) {
            T("Votre véhicule", 28, lineHeight = 36, modifier = Modifier.padding(bottom = 8.dp))
            T("Choisissez la marque, le modèle puis la version et la motorisation, ou cherchez directement.", 14, c.onv, lineHeight = 20, modifier = Modifier.padding(bottom = 16.dp))
            SearchField(query) { query = it }
            if (query.isBlank()) Breadcrumb(brand, model, onRoot = { brandName = null; modelName = null }, onBrand = { modelName = null })
        }
        AnimatedContent(
            targetState = if (query.isNotBlank()) "search" else "${level.name}:${brand?.name}:${model?.name}",
            transitionSpec = {
                val deeper = targetState.count { it == ':' } >= initialState.count { it == ':' } && targetState.length >= initialState.length
                (slideInHorizontally(tween(260)) { if (deeper) it / 6 else -it / 6 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120)) +
                    slideOutHorizontally(tween(260)) { if (deeper) -it / 6 else it / 6 }
            },
            modifier = Modifier.weight(1f),
            label = "picker",
        ) { key ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    key == "search" -> SearchResults(query) { tplId = it.id }
                    level == Level.Brands -> CATALOG.forEach { b ->
                        PickRow(b.name, "${b.models.size} modèle${if (b.models.size > 1) "s" else ""}", initial = b.name.take(1)) {
                            brandName = b.name; modelName = null
                        }
                    }
                    level == Level.Models && brand != null -> brand.models.forEach { m ->
                        val years = m.generations.mapNotNull { it.years.takeIf(String::isNotBlank) }
                        PickRow(m.name, generationsLabel(m), initial = null, sub2 = yearsRange(years)) { modelName = m.name }
                    }
                    model != null && brand != null -> model.generations.forEach { g -> GenerationBlock(brand, model, g) { tplId = it.id } }
                }
            }
        }
    }
}

private fun generationsLabel(m: CatalogModel): String {
    val n = m.generations.size
    val v = m.generations.sumOf { it.variants.size }
    return "$n génération${if (n > 1) "s" else ""} · $v motorisation${if (v > 1) "s" else ""}"
}

private fun yearsRange(years: List<String>): String? {
    if (years.isEmpty()) return null
    val start = years.mapNotNull { it.substringBefore('–').trim().toIntOrNull() }.minOrNull() ?: return null
    val open = years.any { it.endsWith("–") }
    val end = years.mapNotNull { it.substringAfter('–', "").trim().toIntOrNull() }.maxOrNull()
    return if (open || end == null) "Depuis $start" else "$start – $end"
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Cd.c
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Rechercher : « Clio V 1.0 TCe », « Golf GTI »…") },
        leadingIcon = { Ico(Icons.Rounded.Search, 22, c.onv) },
        trailingIcon = { if (value.isNotEmpty()) IconBtn(Icons.Rounded.Close, { onChange("") }, c.onv) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.sf2, unfocusedContainerColor = c.sf2,
            focusedIndicatorColor = c.p, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = c.on, unfocusedTextColor = c.on, cursorColor = c.p,
            focusedPlaceholderColor = c.onv, unfocusedPlaceholderColor = c.onv,
        ),
    )
}

/** Fil d'Ariane : Marques › Suzuki › Swift. */
@Composable
private fun Breadcrumb(brand: CatalogBrand?, model: CatalogModel?, onRoot: () -> Unit, onBrand: () -> Unit) {
    val c = Cd.c
    if (brand == null) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Crumb("Marques", true, onRoot)
        T("›", 16, c.onv, modifier = Modifier.padding(horizontal = 2.dp))
        Crumb(brand.name, model != null, onBrand)
        if (model != null) {
            T("›", 16, c.onv, modifier = Modifier.padding(horizontal = 2.dp))
            Crumb(model.name, false) {}
        }
    }
}

@Composable
private fun Crumb(label: String, link: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    Box(Modifier.clip(RoundedCornerShape(12.dp)).then(if (link) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 6.dp)) {
        T(label, 14, if (link) c.p else c.on, if (link) 500 else 600, maxLines = 1)
    }
}

/** Ligne de liste (marque ou modèle). */
@Composable
private fun PickRow(title: String, sub: String, initial: String?, sub2: String? = null, onClick: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (title == OTHER) c.sf4 else c.pc), contentAlignment = Alignment.Center) {
            if (initial != null && title != OTHER) T(initial, 18, c.opc, 600) else Ico(Icons.Rounded.DirectionsCar, 22, if (title == OTHER) c.onv else c.opc)
        }
        Column(Modifier.weight(1f)) {
            T(title, 16, weight = 500, lineHeight = 22, maxLines = 1)
            T(listOfNotNull(sub, sub2).joinToString(" · "), 12, c.onv, lineHeight = 16, maxLines = 1)
        }
        Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 24, c.onv)
    }
}

/** Une génération et ses motorisations. */
@Composable
private fun GenerationBlock(brand: CatalogBrand, model: CatalogModel, g: CatalogGeneration, onPick: (VehicleTemplate) -> Unit) {
    val c = Cd.c
    val title = if (brand.name == OTHER) g.name else "${model.name} ${g.name}"
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(title, 14, c.p, 600)
        if (g.years.isNotBlank()) T(g.years, 12, c.onv)
    }
    g.variants.forEach { t -> VariantRow(t, null, onPick) }
}

@Composable
private fun VariantRow(t: VehicleTemplate, context: String?, onPick: (VehicleTemplate) -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (t.dedicated) c.sc else c.sf1).clickable { onPick(t) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (context != null) T(context, 12, c.onv, lineHeight = 16, maxLines = 1)
            T(listOf(t.engine, t.power).filter { it.isNotBlank() }.joinToString(" · "), 15, if (t.dedicated) c.osc else c.on, 500, lineHeight = 20)
            T(t.fuel, 12, if (t.dedicated) c.osc else c.onv, lineHeight = 16)
        }
        if (t.dedicated) DedicatedBadge()
        Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 22, c.onv)
    }
}

@Composable
private fun DedicatedBadge() {
    val c = Cd.c
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.p).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Ico(Icons.Rounded.Verified, 14, c.op)
        T("Profil dédié", 11, c.op, 600)
    }
}

@Composable
private fun SearchResults(query: String, onPick: (VehicleTemplate) -> Unit) {
    val c = Cd.c
    val hits = remember(query) { searchCatalog(query) }
    if (hits.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(20.dp)) {
            T("Aucun véhicule trouvé", 16, weight = 500)
            T("Essayez la marque et le modèle seuls, ou choisissez « $OTHER » : les données OBD2 standard fonctionnent sur toutes les voitures essence depuis 2001 et diesel depuis 2004.", 13, c.onv, lineHeight = 18, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    T("${hits.size}${if (hits.size >= 60) "+" else ""} résultat${if (hits.size > 1) "s" else ""}", 13, c.onv, 500, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    hits.forEach { h ->
        val ctx = if (h.brand.name == OTHER) "$OTHER · ${h.generation.name}"
        else "${h.brand.name} ${h.model.name} ${h.generation.name}" + if (h.generation.years.isNotBlank()) " · ${h.generation.years}" else ""
        VariantRow(h.template, ctx, onPick)
    }
}

/** Véhicule choisi : récapitulatif, puis nom du profil, immatriculation et année. */
@Composable
private fun VehicleDetails(vm: AppViewModel, tpl: VehicleTemplate, onChange: () -> Unit, onBack: () -> Unit) {
    val c = Cd.c
    var nickname by rememberSaveable(tpl.id) { mutableStateOf(vm.defaultName(tpl)) }
    var plate by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
            if (!vm.onboarded) T("Étape 1 sur 2", 14, c.onv, 500)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) {
            T("Votre véhicule", 28, lineHeight = 36, modifier = Modifier.padding(bottom = 16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.pc).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 26, c.op) }
                Column(Modifier.weight(1f)) {
                    T(tpl.fullName, 17, c.opc, 600, lineHeight = 22)
                    T(tpl.specs, 13, c.opc)
                }
                IconBtn(Icons.Rounded.Edit, onChange, c.opc)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).clip(RoundedCornerShape(16.dp)).background(c.sf2).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ico(if (tpl.dedicated) Icons.Rounded.Verified else Icons.Rounded.Info, 20, c.onv)
                T(
                    if (tpl.dedicated) "Profil dédié : données constructeur en plus des données OBD2 standard. ${tpl.protocol}."
                    else "Données OBD2 standard : régime, vitesse, températures, pressions, diagnostic… Le protocole est détecté automatiquement à la connexion.",
                    13, c.onv, lineHeight = 18,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field("Nom du profil", nickname, { nickname = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Immatriculation", plate, { plate = it.uppercase() }, Modifier.weight(1.4f), mono = true)
                    Field("Année", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), number = true)
                }
            }
            Box(Modifier.padding(top = 8.dp)) { TextBtn("Changer de véhicule", onChange, Icons.Rounded.Edit) }
        }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)) {
            FilledBtn("Continuer", { vm.createVehicle(tpl.id, nickname.trim(), plate.trim(), year.trim()) }, enabled = nickname.isNotBlank())
        }
    }
}
