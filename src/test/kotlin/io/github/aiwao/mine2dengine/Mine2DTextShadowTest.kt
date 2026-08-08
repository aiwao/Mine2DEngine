package io.github.aiwao.mine2dengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Mine2DTextShadowTest {
    @Test
    fun `glyph quad expands by blur while preserving atlas scale`() {
        val geometry = calculateTextShadowGlyphGeometry(glyphQuad(), blurRadius = 2f)!!

        assertEquals(0.25f, geometry.minU)
        assertEquals(0.4f, geometry.minV)
        assertEquals(0.35f, geometry.maxU)
        assertEquals(0.6f, geometry.maxV)
        assertEquals(0.025f, geometry.uPerGuiUnit, absoluteTolerance = 0.00001f)
        assertEquals(0.025f, geometry.vPerGuiUnit, absoluteTolerance = 0.00001f)
        val expected = listOf(
            vertex(0f, 13f, 0.2f, 0.65f),
            vertex(0f, 1f, 0.2f, 0.35f),
            vertex(8f, 1f, 0.4f, 0.35f),
            vertex(8f, 13f, 0.4f, 0.65f),
        )
        expected.zip(geometry.vertices).forEach { (expectedVertex, actualVertex) ->
            assertEquals(expectedVertex.x, actualVertex.x)
            assertEquals(expectedVertex.y, actualVertex.y)
            assertEquals(expectedVertex.u, actualVertex.u, absoluteTolerance = 0.00001f)
            assertEquals(expectedVertex.v, actualVertex.v, absoluteTolerance = 0.00001f)
            assertEquals(expectedVertex.color, actualVertex.color)
            assertEquals(expectedVertex.light, actualVertex.light)
        }
    }

    @Test
    fun `empty glyph does not create a shadow quad`() {
        assertNull(calculateTextShadowGlyphGeometry(emptyList(), blurRadius = 2f))
    }

    @Test
    fun `glyph shadow geometry rejects invalid inputs`() {
        assertFailsWith<IllegalArgumentException> {
            calculateTextShadowGlyphGeometry(glyphQuad(), blurRadius = -1f)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateTextShadowGlyphGeometry(glyphQuad().dropLast(1), blurRadius = 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateTextShadowGlyphGeometry(
                List(4) { vertex(2f, 3f, 0.25f, 0.4f) },
                blurRadius = 1f,
            )
        }
    }

    private fun glyphQuad(): List<Mine2DTextShadowVertex> = listOf(
        vertex(2f, 11f, 0.25f, 0.6f),
        vertex(2f, 3f, 0.25f, 0.4f),
        vertex(6f, 3f, 0.35f, 0.4f),
        vertex(6f, 11f, 0.35f, 0.6f),
    )

    private fun vertex(
        x: Float,
        y: Float,
        u: Float,
        v: Float,
    ): Mine2DTextShadowVertex = Mine2DTextShadowVertex(
        x = x,
        y = y,
        z = 0f,
        color = 0x80402010.toInt(),
        u = u,
        v = v,
        light = 0x00F000F0,
    )
}
