package com.cardeck.app

import android.app.Application
import android.content.Context
import com.cardeck.app.data.Repo
import com.cardeck.app.obd.ObdManager
import org.osmdroid.config.Configuration
import java.io.File

class CarDeckApp : Application() {
    lateinit var repo: Repo
        private set
    lateinit var obd: ObdManager
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        obd = ObdManager(this)
        Configuration.getInstance().apply {
            load(this@CarDeckApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            // Cache des tuiles OpenStreetMap dans le stockage privé de l'appli (aucune permission requise).
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}

val Context.cardeck: CarDeckApp get() = applicationContext as CarDeckApp
