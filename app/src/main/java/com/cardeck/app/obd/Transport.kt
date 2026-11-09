package com.cardeck.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

/** Liaison brute avec un boîtier ELM327. */
interface ObdTransport {
    val incoming: Channel<ByteArray>
    suspend fun open()
    suspend fun send(data: ByteArray)
    fun close()
}

private fun pump(input: InputStream, out: Channel<ByteArray>) = thread(isDaemon = true, name = "obd-reader") {
    val buf = ByteArray(1024)
    try {
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n > 0) out.trySend(buf.copyOf(n))
        }
    } catch (_: IOException) {
    }
    out.close()
}

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/** Bluetooth classique (profil série SPP). */
@SuppressLint("MissingPermission")
class ClassicTransport(private val ctx: Context, private val address: String) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var socket: BluetoothSocket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: throw IOException("Bluetooth indisponible sur ce téléphone")
        if (!bt.isEnabled) throw IOException("Le Bluetooth est désactivé")
        runCatching { bt.cancelDiscovery() }
        val dev = bt.getRemoteDevice(address)
        val s = try {
            dev.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            // Repli utilisé par de nombreux clones ELM327 (canal RFCOMM 1).
            try {
                val m = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (m.invoke(dev, 1) as BluetoothSocket).also { it.connect() }
            } catch (_: Exception) {
                throw IOException("Impossible de joindre le boîtier (est-il branché et le contact mis ?)", e)
            }
        }
        socket = s
        pump(s.inputStream, incoming)
        Unit
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.outputStream ?: throw IOException("Non connecté")
        out.write(data); out.flush()
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        incoming.close()
    }
}

/** Wi-Fi : socket TCP liée au réseau Wi-Fi du boîtier (qui n'a pas d'accès Internet). */
class WifiTransport(private val ctx: Context, private val host: String, private val port: Int) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var socket: Socket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val wifi = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            ?: throw IOException("Connectez d'abord le téléphone au Wi-Fi du boîtier")
        val s = wifi.socketFactory.createSocket()
        try {
            s.connect(InetSocketAddress(host, port), 6000)
        } catch (e: IOException) {
            runCatching { s.close() }
            throw IOException("Boîtier injoignable sur $host:$port", e)
        }
        socket = s
        pump(s.getInputStream(), incoming)
        Unit
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.getOutputStream() ?: throw IOException("Non connecté")
        out.write(data); out.flush()
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        incoming.close()
    }
}

private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Bluetooth Low Energy : on cherche automatiquement le service « série » du boîtier
 * (une caractéristique de notification + une d'écriture, ex. FFF0/FFF1/FFF2, FFE0/FFE1, 18F0/2AF0/2AF1).
 */
@SuppressLint("MissingPermission")
class BleTransport(private val ctx: Context, private val address: String) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val ready = CompletableDeferred<Unit>()
    @Volatile private var writeAck: CompletableDeferred<Unit>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!g.requestMtu(185)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!ready.isCompleted) ready.completeExceptionally(IOException("Connexion BLE refusée (code $status)"))
                incoming.close()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@BleTransport.mtu = mtu
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val generic = setOf("00001800", "00001801", "0000180a")
            var notify: BluetoothGattCharacteristic? = null
            var write: BluetoothGattCharacteristic? = null
            for (svc in g.services) {
                if (svc.uuid.toString().take(8) in generic) continue
                val n = svc.characteristics.firstOrNull { it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 }
                val w = svc.characteristics.firstOrNull { it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }
                if (n != null && w != null) { notify = n; write = w; break }
            }
            if (notify == null || write == null) {
                ready.completeExceptionally(IOException("Ce périphérique BLE n'expose pas d'interface OBD2"))
                return
            }
            writeChar = write
            g.setCharacteristicNotification(notify, true)
            val desc = notify.getDescriptor(CCCD)
            if (desc == null) { ready.complete(Unit); return }
            val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(desc, value)
            } else {
                @Suppress("DEPRECATION")
                desc.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            ready.complete(Unit)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeAck?.complete(Unit)
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) c.value?.let { incoming.trySend(it.copyOf()) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            incoming.trySend(value.copyOf())
        }
    }

    override suspend fun open() {
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: throw IOException("Bluetooth indisponible sur ce téléphone")
        if (!bt.isEnabled) throw IOException("Le Bluetooth est désactivé")
        val dev = bt.getRemoteDevice(address)
        gatt = withContext(Dispatchers.Main) { dev.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE) }
        val ok = withTimeoutOrNull(15_000) { ready.await() }
        if (ok == null) {
            close()
            throw IOException("Le boîtier BLE ne répond pas")
        }
    }

    override suspend fun send(data: ByteArray) {
        val g = gatt ?: throw IOException("Non connecté")
        val c = writeChar ?: throw IOException("Non connecté")
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val chunk = (mtu - 3).coerceAtLeast(20)
        var i = 0
        while (i < data.size) {
            val part = data.copyOfRange(i, minOf(data.size, i + chunk))
            val ack = CompletableDeferred<Unit>()
            writeAck = ack
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, part, type) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = type
                @Suppress("DEPRECATION")
                c.value = part
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
            if (!started) throw IOException("Écriture BLE impossible")
            withTimeoutOrNull(2000) { ack.await() } ?: throw IOException("Écriture BLE sans réponse")
            i += chunk
        }
    }

    override fun close() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        incoming.close()
    }
}
