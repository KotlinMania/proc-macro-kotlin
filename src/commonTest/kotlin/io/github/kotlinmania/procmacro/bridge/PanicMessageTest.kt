package io.github.kotlinmania.procmacro.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PanicMessageTest {
    @Test
    fun asStrPreservesBothTextVariantsAndUnknown() {
        assertEquals("static", PanicMessage.StaticStr("static").asStr())
        assertEquals("owned", PanicMessage.String("owned").asStr())
        assertEquals("", PanicMessage.StaticStr("").asStr())
        assertEquals("", PanicMessage.String("").asStr())
        assertNull(PanicMessage.Unknown.asStr())
    }

    @Test
    fun intoStringPreservesBothTextVariantsAndUnknown() {
        assertEquals("static", PanicMessage.StaticStr("static").intoString())
        assertEquals("owned", PanicMessage.String("owned").intoString())
        assertEquals("", PanicMessage.StaticStr("").intoString())
        assertEquals("", PanicMessage.String("").intoString())
        assertNull(PanicMessage.Unknown.intoString())
    }
}
