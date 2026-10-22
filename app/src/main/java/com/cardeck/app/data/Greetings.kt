package com.cardeck.app.data

/** Messages d'accueil, un différent à chaque lancement de l'appli. */
object Greetings {
    val all = listOf(
        "Prêt à faire chanter le turbo ?",
        "Le moteur est chaud, toi aussi ?",
        "Aucun voyant allumé… pour l'instant.",
        "On vérifie l'huile ou on fait confiance ?",
        "Rappel : le frein, c'est la pédale du milieu.",
        "La vitesse, c'est bien. Arriver, c'est mieux.",
        "Les PIDs sont prêts, et toi ?",
        "Le plein ou le prêt à rouler ?",
        "Ta voiture a des choses à te dire.",
        "Les clignotants, c'est gratuit.",
        "Moins de tours, plus de sous.",
        "Le turbo ne siffle pas tout seul.",
        "Un bon conducteur regarde la route, pas l'appli.",
        "Encore un trajet pour l'historique ?",
        "Le compte-tours n'est pas un défi.",
        "On roule, on mesure, on se la raconte.",
        "La consommation, c'est une question de point de vue.",
        "Diagnostic du jour : conducteur en pleine forme.",
        "Ton ELM327 t'attend depuis tout à l'heure.",
        "Bouclé ? Parfait, on peut parler chiffres.",
    )

    /** Message tiré au hasard, différent du précédent ([last] = index affiché la dernière fois). */
    fun pick(last: Int): Int {
        if (all.size < 2) return 0
        var i: Int
        do { i = all.indices.random() } while (i == last)
        return i
    }
}
