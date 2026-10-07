package com.jarvis.clock

import android.view.KeyEvent

/**
 * Decisión pura (sin Android en runtime) sobre los botones físicos del reloj.
 * MainActivity la consulta en dispatchKeyEvent, antes de la jerarquía de vistas, porque el
 * WebView del motor de pantalla se traga KEYCODE_MUTE si llega a él.
 */
object HardwareKeys {
    enum class Action { VOLUME_UP, VOLUME_DOWN, MIC_MUTE_TOGGLE, NONE }

    fun decide(keyCode: Int, action: Int, repeatCount: Int): Action {
        if (action != KeyEvent.ACTION_DOWN) return Action.NONE
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Action.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> Action.VOLUME_DOWN
            // Botón central mantenido: solo la primera pulsación alterna, sin flip-flop.
            KeyEvent.KEYCODE_MUTE -> if (repeatCount == 0) Action.MIC_MUTE_TOGGLE else Action.NONE
            else -> Action.NONE
        }
    }

    /** Teclas que nunca deben llegar al WebView (incluye ACTION_UP y repeticiones). */
    fun consumes(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_MUTE
}
