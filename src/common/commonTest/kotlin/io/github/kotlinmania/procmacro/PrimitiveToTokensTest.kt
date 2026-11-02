package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class PrimitiveToTokensTest {
    @Test
    fun unsignedLimitsKeepTheirWidths() {
        val cases =
            listOf(
                UByte.MAX_VALUE.asU8ToTokens() to "255u8",
                UShort.MAX_VALUE.asU16ToTokens() to "65535u16",
                UInt.MAX_VALUE.asU32ToTokens() to "4294967295u32",
                ULong.MAX_VALUE.asU64ToTokens() to "18446744073709551615u64",
            )
        for ((adapter, expected) in cases) {
            val stream = adapter.toTokenStream()
            assertIs<TokenTree.Literal>(stream.single())
            assertEquals(expected, stream.toString())
        }
    }

    @Test
    fun signedLimitsKeepTheirWidths() {
        val cases =
            listOf(
                Byte.MIN_VALUE.asI8ToTokens() to "-128i8",
                Short.MIN_VALUE.asI16ToTokens() to "-32768i16",
                Int.MIN_VALUE.asI32ToTokens() to "-2147483648i32",
                Long.MIN_VALUE.asI64ToTokens() to "-9223372036854775808i64",
            )
        for ((adapter, expected) in cases) {
            assertEquals(expected, adapter.toTokenStream().toString())
        }
    }

    @Test
    fun pointerSizedIntegersKeepDistinctSuffixes() {
        val unsigned = ULong.MAX_VALUE.asUsizeToTokens().toTokenStream()
        val signed = Long.MIN_VALUE.asIsizeToTokens().toTokenStream()
        assertEquals("18446744073709551615usize", unsigned.toString())
        assertEquals("-9223372036854775808isize", signed.toString())
    }

    @Test
    fun booleansProduceIdentifiers() {
        for ((value, expected) in listOf(true to "true", false to "false")) {
            val tree = value.asBoolToTokens().toTokenStream().single()
            assertIs<TokenTree.Ident>(tree)
            assertEquals(expected, tree.toString())
        }
    }

    @Test
    fun floatWidthsAndNegativeZeroArePreserved() {
        assertEquals("-0.0f32", (-0.0f).asF32ToTokens().toTokenStream().toString())
        assertEquals("-0.0f64", (-0.0).asF64ToTokens().toTokenStream().toString())
    }

    @Test
    fun nonFiniteFloatsFailDuringEmission() {
        val adapters =
            listOf(
                Float.NaN.asF32ToTokens(),
                Float.POSITIVE_INFINITY.asF32ToTokens(),
                Float.NEGATIVE_INFINITY.asF32ToTokens(),
                Double.NaN.asF64ToTokens(),
                Double.POSITIVE_INFINITY.asF64ToTokens(),
                Double.NEGATIVE_INFINITY.asF64ToTokens(),
            )
        for (adapter in adapters) {
            val destination = Ident.new("prefix", Span.callSite()).asToTokens().toTokenStream()
            assertFailsWith<IllegalArgumentException> { adapter.toTokens(destination) }
            assertEquals("prefix", destination.toString())
        }
    }

    @Test
    fun emissionAppendsAndRepeatedConversionsProduceIndependentStreams() {
        val adapter = 42.asI32ToTokens()
        val first = adapter.toTokenStream()
        val second = adapter.intoTokenStream()
        assertNotSame(first, second)
        adapter.toTokens(first)
        assertEquals("42i32 42i32", first.toString())
        assertEquals("42i32", second.toString())
    }
}
