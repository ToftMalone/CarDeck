package com.cardeck.app.data

import android.content.Context

/** Messages d'accueil (fichier assets/greetings.txt, un par ligne), un différent à chaque lancement de l'appli. */
object Greetings {
    private var cache: List<String>? = null

    fun all(ctx: Context): List<String> = cache ?: runCatching {
        ctx.assets.open("greetings.txt").bufferedReader(Charsets.UTF_8).readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList()).ifEmpty { listOf("Bonne route !") }.also { cache = it }

    /** Index tiré au hasard parmi [size] messages, différent du précédent ([last]). */
    fun pick(size: Int, last: Int): Int {
        if (size < 2) return 0
        var i: Int
        do { i = (0 until size).random() } while (i == last)
        return i
    }
}
