package io.github.aiwao.mine2dengine

import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Mine2DFontTest {
    @Test
    fun `text drawing API has no legacy drop shadow parameter`() {
        val method = Mine2DEngine::class.java.methods.single { candidate ->
            candidate.name == "text"
        }

        assertEquals(
            listOf(
                Mine2DFont::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
            method.parameterTypes.toList(),
        )
    }

    @Test
    fun `rejects a resource without a ttf extension before accessing the renderer`() {
        assertFailsWith<IllegalArgumentException> {
            Mine2DFont.load(Identifier.fromNamespaceAndPath("test", "font.otf"))
        }
    }

    @Test
    fun `rejects an invalid size before accessing the renderer`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { size ->
            assertFailsWith<IllegalArgumentException> {
                Mine2DFont.load(Identifier.fromNamespaceAndPath("test", "font.ttf"), size)
            }
        }
    }

    @Test
    fun `rejects an invalid oversample before accessing the renderer`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { oversample ->
            assertFailsWith<IllegalArgumentException> {
                Mine2DFont.load(
                    Identifier.fromNamespaceAndPath("test", "font.ttf"),
                    size = 11f,
                    oversample = oversample,
                )
            }
        }
    }
}
