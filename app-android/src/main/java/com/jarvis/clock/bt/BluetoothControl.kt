package com.jarvis.clock.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Control del Bluetooth para usar el reloj como ALTAVOZ del móvil.
 *
 * Para que el audio del móvil salga por el altavoz del reloj, el reloj tiene que
 * exponer el perfil **A2DP sink** (rol receptor). Android por defecto es *source*.
 * Este módulo:
 *   - enciende el Bluetooth,
 *   - pone el reloj **visible** para poder emparejar el móvil,
 *   - informa del estado (visible, emparejados, si hay un móvil conectado como fuente A2DP).
 *
 * Si tu GSI no trae el A2DP sink activado, el emparejamiento funciona pero el audio
 * no se enruta; ver docs/altavoz-bluetooth.md para activarlo en el ROM.
 */
object BluetoothControl {
    private const val TAG = "BluetoothControl"
    // Constantes ocultas del framework (no expuestas en el SDK).
    private const val PROFILE_A2DP_SINK = 11
    private const val SCAN_MODE_CONNECTABLE_DISCOVERABLE = 23

    private fun adapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Enciende el Bluetooth. Devuelve true si ya está o se pudo iniciar. */
    @SuppressLint("MissingPermission")
    fun enable(ctx: Context): Boolean {
        val a = adapter(ctx) ?: return false
        if (a.isEnabled) return true
        // enable() está deprecado pero funciona en Android 10 (el reloj es API 29).
        return runCatching { a.enable() }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun disable(ctx: Context): Boolean {
        val a = adapter(ctx) ?: return false
        if (!a.isEnabled) return true
        return runCatching { a.disable() }.getOrDefault(false)
    }

    /**
     * Pone el reloj en modo altavoz: enciende BT y lo hace visible [seconds] segundos.
     * Intenta hacerlo sin diálogo (por reflexión, si somos app de sistema/rooteada);
     * si no se puede, [needsUiIntent] devuelve un Intent para que la UI lo pida.
     */
    @SuppressLint("MissingPermission")
    fun speakerMode(ctx: Context, seconds: Int = 300): Result {
        val a = adapter(ctx) ?: return Result(false, "El reloj no tiene Bluetooth.")
        if (!a.isEnabled) { a.enable(); }
        val ok = trySetDiscoverable(a, seconds)
        return if (ok) {
            Result(true, "Bluetooth visible ${seconds}s. Empareja el reloj desde tu móvil.")
        } else {
            Result(false, "Enciende la visibilidad desde el reloj (toca 'Hacer visible').", needsUi = true)
        }
    }

    /** Intent de respaldo para pedir visibilidad con el diálogo del sistema. */
    fun discoverableIntent(seconds: Int = 300): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds)

    @SuppressLint("MissingPermission")
    private fun trySetDiscoverable(a: BluetoothAdapter, seconds: Int): Boolean {
        // setScanMode es @hide y exige BLUETOOTH_PRIVILEGED (solo apps de sistema).
        // Probamos por reflexión; si lanza SecurityException, tocará el diálogo.
        for (types in listOf(
            arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            arrayOf(Int::class.javaPrimitiveType),
        )) {
            try {
                val m = BluetoothAdapter::class.java.getMethod("setScanMode", *types)
                val r = if (types.size == 2) m.invoke(a, SCAN_MODE_CONNECTABLE_DISCOVERABLE, seconds)
                else m.invoke(a, SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                if (r == null || r == true) return true
            } catch (e: Exception) {
                Log.d(TAG, "setScanMode(${types.size}) no disponible: ${e.message}")
            }
        }
        return a.scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE
    }

    @SuppressLint("MissingPermission")
    fun status(ctx: Context): JSONObject {
        val a = adapter(ctx)
        val o = JSONObject()
        if (a == null) return o.put("available", false)
        o.put("available", true)
        o.put("enabled", a.isEnabled)
        o.put("discoverable", runCatching { a.scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE }.getOrDefault(false))
        // ¿Hay un móvil conectado como fuente A2DP (reloj = sink)?
        val sinkState = runCatching { a.getProfileConnectionState(PROFILE_A2DP_SINK) }.getOrDefault(0)
        o.put("phone_connected", sinkState == BluetoothAdapter.STATE_CONNECTED)
        val paired = JSONArray()
        runCatching {
            a.bondedDevices?.forEach { d: BluetoothDevice ->
                paired.put(JSONObject().put("name", d.name ?: "?").put("address", d.address))
            }
        }
        o.put("paired", paired)
        return o
    }

    data class Result(val ok: Boolean, val message: String, val needsUi: Boolean = false)
}
