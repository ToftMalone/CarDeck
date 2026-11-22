package com.cardeck.app.sim

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.cardeck.app.obd.DebugTools

/**
 * Branche le simulateur au démarrage de l'application, en version de développement seulement : ce fournisseur n'est
 * déclaré que par le manifeste du dossier « debug ». Une seule voiture simulée par lancement de l'appli : ses défauts
 * effacés et ses kilomètres restent d'une connexion à l'autre.
 */
class SimInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        DebugTools.simulator = { SimTransport(car) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val car by lazy { SimCar() }
    }
}
