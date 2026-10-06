package com.jarvis.clock

import com.jarvis.clock.design.DesignSchema
import com.jarvis.clock.design.DesignStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignStoreTest {
    private val initial = { DesignSchema.initial(70, 3, 23, 7) }
    private fun themeName(l: DesignStore.Loaded) =
        l.design.getJSONObject("themes").getJSONObject("clasico").getString("name")

    @Test fun `sin nada guardado se usa el inicial`() {
        val l = DesignStore.decode(null, initial)
        assertFalse(l.saved)
        assertEquals("Clásico", themeName(l))
    }

    @Test fun `un diseno guardado corrupto o inválido cae al inicial`() {
        assertFalse(DesignStore.decode("esto no es json", initial).saved)
        assertFalse(DesignStore.decode("{\"version\":2}", initial).saved)
        assertFalse(DesignStore.decode("", initial).saved)
        val roto = initial().apply { getJSONArray("profiles").getJSONObject(0).put("theme", "fantasma") }
        val l = DesignStore.decode(roto.toString(), initial)
        assertFalse(l.saved)
        assertTrue(DesignSchema.validate(l.design) is DesignSchema.Result.Ok)
    }

    @Test fun `un diseno guardado valido se respeta`() {
        val mio = initial().apply { getJSONObject("themes").getJSONObject("clasico").put("name", "MiTema") }
        val l = DesignStore.decode(mio.toString(), initial)
        assertTrue(l.saved)
        assertEquals("MiTema", themeName(l))
    }
}
