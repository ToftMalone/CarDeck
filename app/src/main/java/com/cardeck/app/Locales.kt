package com.cardeck.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Langue de l'application choisie dans les réglages ("" = celle du système). */
object Locales {
    fun wrap(base: Context): Context {
        val tag = base.getSharedPreferences("cardeck", Context.MODE_PRIVATE).getString("language", "") ?: ""
        if (tag.isEmpty()) {
            Locale.setDefault(android.content.res.Resources.getSystem().configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(locale)
        return base.createConfigurationContext(conf)
    }
}
