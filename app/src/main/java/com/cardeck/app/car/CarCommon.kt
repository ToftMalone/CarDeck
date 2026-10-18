package com.cardeck.app.car

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cardeck.app.AppViewModel
import com.cardeck.app.cardeck
import com.cardeck.app.data.Prefs
import com.cardeck.app.data.VehicleProfile
import com.cardeck.app.obd.ObdState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Icônes Material converties pour l'écran de la voiture (mises en cache). */
internal object CarIcons {
    val GREEN: CarColor = CarColor.createCustom(0xFF0E9F6E.toInt(), 0xFF3DDC97.toInt())
    val GREY: CarColor = CarColor.createCustom(0xFF80868B.toInt(), 0xFF80868B.toInt())
    private const val PX = 128
    private val cache = HashMap<String, CarIcon>()

    fun of(v: ImageVector, tint: CarColor = GREEN): CarIcon = cache.getOrPut("${v.name}|${tint == GREY}") {
        CarIcon.Builder(IconCompat.createWithBitmap(v.toBitmap(PX))).setTint(tint).build()
    }

    /** Dessine une icône Material (ImageVector) dans un bitmap blanc, teinté ensuite par l'hôte. */
    private fun ImageVector.toBitmap(px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(px / viewportWidth, px / viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        fun draw(g: VectorGroup) {
            for (n in g) when (n) {
                is VectorPath -> {
                    val path = PathParser().addPathNodes(n.pathData).toPath().asAndroidPath()
                    path.fillType = if (n.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                    canvas.drawPath(path, paint)
                }
                is VectorGroup -> draw(n)
            }
        }
        draw(root)
        return bmp
    }
}

/**
 * Véhicule en mouvement : vitesse fournie par Android Auto (capteurs de la voiture),
 * à défaut celle lue sur la prise OBD2.
 */
internal object CarMotion {
    private const val PERM = "com.google.android.gms.permission.CAR_SPEED"
    private const val MOVING_KMH = 5.0

    @Volatile private var carKmh: Double? = null
    private var listener: OnCarDataAvailableListener<Speed>? = null

    fun moving(ctx: CarContext): Boolean {
        val obd = ctx.cardeck.obd
        val kmh = carKmh ?: obd.live.value["std0D"]?.takeIf { obd.state.value is ObdState.Connected }
        return (kmh ?: 0.0) >= MOVING_KMH
    }

    fun start(ctx: CarContext) {
        if (ContextCompat.checkSelfPermission(ctx, PERM) == PackageManager.PERMISSION_GRANTED) { listen(ctx); return }
        val prefs = Prefs(ctx)
        if (prefs.carSpeedAsked) return
        prefs.carSpeedAsked = true
        ctx.requestPermissions(listOf(PERM)) { granted, _ -> if (PERM in granted) listen(ctx) }
    }

    private fun listen(ctx: CarContext) {
        if (listener != null) return
        val l = OnCarDataAvailableListener<Speed> { s ->
            val v = s.displaySpeedMetersPerSecond.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value
                ?: s.rawSpeedMetersPerSecond.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value
            carKmh = v?.let { abs(it) * 3.6 }
        }
        runCatching {
            ctx.getCarService(CarHardwareManager::class.java).carInfo.addSpeedListener(ContextCompat.getMainExecutor(ctx), l)
            listener = l
        }
    }

    fun stop(ctx: CarContext) {
        listener?.let { l -> runCatching { ctx.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l) } }
        listener = null
        carKmh = null
    }
}

/**
 * Écran CarDeck sur Android Auto. Il est redessiné quand [snapshot] change (vérifié chaque seconde) ;
 * s'il est réservé à l'arrêt ([parkedOnly]), il se referme dès que le véhicule roule.
 */
internal abstract class CarScreen(ctx: CarContext, private val parkedOnly: Boolean = true) : Screen(ctx) {
    protected val app = ctx.cardeck
    protected val vm: AppViewModel get() = app.vm
    protected val active: VehicleProfile? get() = app.repo.active
    private var shown: Any? = Unit

    /** Ce qui est affiché : l'écran est redessiné quand cette valeur change. */
    protected open suspend fun snapshot(): Any? = null

    /** Travail mené tant que l'écran est visible (lecture des données OBD2…). */
    protected open suspend fun whileVisible() {}

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { whileVisible() }
                while (true) {
                    if (parkedOnly && CarMotion.moving(carContext) && screenManager.top === this@CarScreen) {
                        toast("Disponible uniquement à l'arrêt")
                        screenManager.popToRoot()
                    }
                    // Messages de l'appli (diagnostic, codage…) affichés aussi sur l'écran de la voiture.
                    val sn = vm.snack
                    if (screenManager.top === this@CarScreen) {
                        if (sn != null && sn != lastSnack) toast(sn)
                        lastSnack = sn
                    }
                    val s = snapshot()
                    if (s != shown) {
                        shown = s
                        invalidate()
                    }
                    delay(1000)
                }
            }
        }
    }

    fun toast(msg: CharSequence) = CarToast.makeText(carContext, msg, CarToast.LENGTH_LONG).show()

    fun push(s: Screen) = screenManager.push(s)

    /** Nombre maximal d'éléments d'une liste ou d'une grille sur cet écran de voiture. */
    fun limit(type: Int): Int =
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            runCatching { carContext.getCarService(ConstraintManager::class.java).getContentLimit(type) }.getOrDefault(6)
        } else 6

    /** Libellé de l'état de la liaison avec le boîtier du véhicule actif. */
    fun connLabel(v: VehicleProfile? = active): String {
        val a = v?.adapter ?: return "Aucun boîtier"
        return when (val s = app.obd.state.value) {
            is ObdState.Connected -> if (s.adapter.address == a.address) "Connecté" else "Hors ligne"
            is ObdState.Connecting -> if (s.adapter.address == a.address) "Connexion…" else "Hors ligne"
            else -> "Hors ligne"
        }
    }

    val connected: Boolean
        get() {
            val a = active?.adapter ?: return false
            return (app.obd.state.value as? ObdState.Connected)?.adapter?.address == a.address
        }

    /** Message simple avec retour (écran sans contenu, aucun véhicule…). */
    fun message(title: String, text: String, vararg actions: Action): Template =
        MessageTemplate.Builder(text).setTitle(title).setHeaderAction(Action.BACK).apply { actions.forEach { addAction(it) } }.build()
}

/** Dernier message de l'appli déjà affiché sur l'écran de la voiture. */
private var lastSnack: String? = null

internal fun action(title: String, primary: Boolean = false, onClick: () -> Unit): Action =
    Action.Builder().setTitle(title).setOnClickListener(onClick).apply { if (primary) setBackgroundColor(CarColor.GREEN) }.build()

/** Confirmation d'une action (effacement, écriture…). */
internal class ConfirmCarScreen(
    ctx: CarContext,
    private val title: String,
    private val text: String,
    private val confirm: String,
    private val danger: Boolean = false,
    private val onConfirm: (ConfirmCarScreen) -> Unit,
) : CarScreen(ctx) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder(text)
        .setTitle(title)
        .setHeaderAction(Action.BACK)
        .addAction(
            Action.Builder().setTitle(confirm).setBackgroundColor(if (danger) CarColor.RED else CarColor.GREEN)
                .setOnClickListener { onConfirm(this) }.build(),
        )
        .addAction(action("Annuler") { screenManager.pop() })
        .build()
}
