package com.cardeck.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.IntentCompat
import com.cardeck.app.cardeck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Réveil de CarDeck (sortie de veille), selon le choix fait pour chaque véhicule. */
object Wake {
    private const val JOB_AUTO = 7

    /** Android Auto : état publié par l'hôte (même source que CarConnection de la Car App Library). */
    private val CAR_CONNECTION = Uri.parse("content://androidx.car.app.connection")
    private const val PROJECTION = 2

    /** Sans optimisation de batterie, Android autorise le réveil du suivi depuis l'arrière-plan. */
    fun batteryOk(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun androidAutoConnected(ctx: Context): Boolean = runCatching {
        ctx.contentResolver.query(CAR_CONNECTION, arrayOf("CarConnectionState"), null, null, null)?.use { c ->
            c.moveToFirst() && c.getInt(0) == PROJECTION
        } ?: false
    }.getOrDefault(false)

    /** Sort le véhicule de veille : il devient le véhicule actif et le suivi démarre. */
    fun wake(ctx: Context, vehicleId: Long) {
        if (TripService.live.value != null) return
        ctx.cardeck.repo.select(vehicleId)
        TripService.start(ctx)
    }

    /** Android Auto démarre quelques secondes après la connexion Bluetooth : surveillance pendant 3 minutes. */
    fun watchAndroidAuto(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        js.schedule(JobInfo.Builder(JOB_AUTO, ComponentName(ctx, AndroidAutoWakeJob::class.java)).setOverrideDeadline(1_000).build())
    }
}

/** Connexion Bluetooth du téléphone : réveil si c'est l'appareil choisi pour un véhicule, ou attente d'Android Auto. */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED || TripService.live.value != null) return
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val repo = ctx.cardeck.repo
        val vehicles = repo.vehicles.value
        vehicles.firstOrNull { repo.wake(it.id).let { w -> w.btReady && w.btAddress.equals(device.address, ignoreCase = true) } }
            ?.let { Wake.wake(ctx, it.id); return }
        if (vehicles.any { repo.wake(it.id).androidAuto }) Wake.watchAndroidAuto(ctx)
    }
}

/** Attend qu'Android Auto soit connecté (au plus 3 minutes) puis réveille le véhicule configuré pour Android Auto. */
class AndroidAutoWakeJob : JobService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            repeat(36) {
                if (TripService.live.value != null) return@repeat
                if (Wake.androidAutoConnected(this@AndroidAutoWakeJob)) {
                    val repo = cardeck.repo
                    val v = repo.active?.takeIf { repo.wake(it.id).androidAuto }
                        ?: repo.vehicles.value.firstOrNull { repo.wake(it.id).androidAuto }
                    v?.let { Wake.wake(this@AndroidAutoWakeJob, it.id) }
                    jobFinished(params, false)
                    return@launch
                }
                delay(5_000)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
