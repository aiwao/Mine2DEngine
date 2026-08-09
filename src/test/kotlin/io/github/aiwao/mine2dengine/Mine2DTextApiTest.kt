package io.github.aiwao.mine2dengine

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Mine2DTextApiTest {
    private val floatType = Float::class.javaPrimitiveType

    @Test
    fun `text drawing coordinates use floats`() {
        val text = Mine2DEngine::class.java.methods.single {
            it.name == "text" && it.parameterCount == 5
        }
        val textShadow = Mine2DEngine::class.java.methods.single {
            it.name == "textShadow" && it.parameterCount == 8
        }

        assertContentEquals(arrayOf(floatType, floatType), text.parameterTypes.sliceArray(2..3))
        assertContentEquals(
            arrayOf(floatType, floatType),
            textShadow.parameterTypes.sliceArray(2..3),
        )
    }

    @Test
    fun `text measurements use floats`() {
        val width = Mine2DFont::class.java.getMethod("width", String::class.java)
        val lineHeight = Mine2DFont::class.java.getMethod("getLineHeight")

        assertEquals(floatType, width.returnType)
        assertEquals(floatType, lineHeight.returnType)
    }
}
