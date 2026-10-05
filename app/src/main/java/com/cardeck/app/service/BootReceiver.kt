package com.cardeck.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance le suivi automatique des trajets après un redémarrage du téléphone ou une mise à jour. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TripService.start(context)
        }
    }
}
