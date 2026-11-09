package com.cardeck.app.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.IntentCompat
import com.cardeck.app.cardeck

/** Réveil de CarDeck (sortie de veille) à la connexion Bluetooth choisie pour un véhicule. */
object Wake {
    /** Sans optimisation de batterie, Android autorise le réveil du suivi depuis l'arrière-plan. */
    fun batteryOk(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Sort le véhicule de veille : il devient le véhicule actif et le suivi démarre. */
    fun wake(ctx: Context, vehicleId: Long) {
        if (TripService.live.value != null) return
        ctx.cardeck.repo.select(vehicleId)
        TripService.start(ctx)
    }
}

/** Connexion Bluetooth du téléphone : réveil si c'est l'appareil choisi pour un véhicule. */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED || TripService.live.value != null) return
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val repo = ctx.cardeck.repo
        repo.vehicles.value.firstOrNull { repo.wake(it.id).let { w -> w.btReady && w.btAddress.equals(device.address, ignoreCase = true) } }
            ?.let { Wake.wake(ctx, it.id) }
    }
}
