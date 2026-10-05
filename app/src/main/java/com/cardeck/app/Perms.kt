package com.cardeck.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val bluetooth: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun bluetoothOk(ctx: Context) = bluetooth.all { has(ctx, it) }

    fun locationOk(ctx: Context) = has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

    fun backgroundLocationOk(ctx: Context) = Build.VERSION.SDK_INT < 29 || has(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun notificationsOk(ctx: Context) = Build.VERSION.SDK_INT < 33 || has(ctx, Manifest.permission.POST_NOTIFICATIONS)

    /** Tout ce qu'il faut pour enregistrer les trajets automatiquement. */
    fun tripsOk(ctx: Context) = locationOk(ctx) && backgroundLocationOk(ctx) && notificationsOk(ctx)
}
