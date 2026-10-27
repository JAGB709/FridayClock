package com.jarvis.clock

import com.jarvis.clock.ui.Steps
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetsTest {
    @Test fun `el selector da la vuelta en los extremos`() {
        assertEquals(0, Steps.wrap(23, 1, 0, 23))
        assertEquals(23, Steps.wrap(0, -1, 0, 23))
        assertEquals(59, Steps.wrap(58, 1, 0, 59))
        assertEquals(5, Steps.wrap(4, 1, 0, 59))
    }

    @Test fun `un salto grande tambien da la vuelta`() {
        assertEquals(2, Steps.wrap(58, 4, 0, 59))
        assertEquals(58, Steps.wrap(2, -4, 0, 59))
    }

    @Test fun `el formato de dos cifras`() {
        assertEquals("07", Steps.two(7))
        assertEquals("23", Steps.two(23))
    }
}
