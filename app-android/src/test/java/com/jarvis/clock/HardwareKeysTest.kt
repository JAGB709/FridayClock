package com.jarvis.clock

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeysTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test fun silencioAlPulsarAlternaMicro() {
        assertEquals(HardwareKeys.Action.MIC_MUTE_TOGGLE, HardwareKeys.decide(KeyEvent.KEYCODE_MUTE, down, 0))
    }

    @Test fun silencioMantenidoNoAlternaDeNuevo() {
        assertEquals(HardwareKeys.Action.NONE, HardwareKeys.decide(KeyEvent.KEYCODE_MUTE, down, 1))
    }

    @Test fun silencioAlSoltarEsNadaPeroSeConsume() {
        assertEquals(HardwareKeys.Action.NONE, HardwareKeys.decide(KeyEvent.KEYCODE_MUTE, up, 0))
        assertTrue(HardwareKeys.consumes(KeyEvent.KEYCODE_MUTE))
    }

    @Test fun volumenAdmiteRepeticiones() {
        assertEquals(HardwareKeys.Action.VOLUME_UP, HardwareKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, down, 0))
        assertEquals(HardwareKeys.Action.VOLUME_UP, HardwareKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, down, 5))
        assertEquals(HardwareKeys.Action.VOLUME_DOWN, HardwareKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, down, 0))
        assertEquals(HardwareKeys.Action.VOLUME_DOWN, HardwareKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, down, 7))
    }

    @Test fun volumenAlSoltarEsNadaPeroSeConsume() {
        assertEquals(HardwareKeys.Action.NONE, HardwareKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, up, 0))
        assertTrue(HardwareKeys.consumes(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(HardwareKeys.consumes(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test fun otrasTeclasNoSeTocan() {
        assertEquals(HardwareKeys.Action.NONE, HardwareKeys.decide(KeyEvent.KEYCODE_BACK, down, 0))
        assertEquals(HardwareKeys.Action.NONE, HardwareKeys.decide(KeyEvent.KEYCODE_DPAD_CENTER, down, 0))
        assertFalse(HardwareKeys.consumes(KeyEvent.KEYCODE_BACK))
        assertFalse(HardwareKeys.consumes(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
