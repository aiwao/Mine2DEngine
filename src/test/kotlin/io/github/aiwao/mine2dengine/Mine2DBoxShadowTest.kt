package io.github.aiwao.mine2dengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Mine2DBoxShadowTest {
    @Test
    fun `box shadow geometry applies offset spread blur and corner growth`() {
        val geometry = calculateBoxShadowGeometry(
            x = 10f,
            y = 20f,
            width = 100f,
            height = 40f,
            offsetX = 3f,
            offsetY = 5f,
            blurRadius = 4f,
            spreadRadius = 2f,
            cornerRadius = 8f,
        )!!

        assertEquals(7f, geometry.left)
        assertEquals(19f, geometry.top)
        assertEquals(112f, geometry.width)
        assertEquals(52f, geometry.height)
        assertEquals(119f, geometry.right)
        assertEquals(71f, geometry.bottom)
        assertEquals(104f, geometry.shadowWidth)
        assertEquals(44f, geometry.shadowHeight)
        assertEquals(10f, geometry.cornerRadius)
    }

    @Test
    fun `box shadow geometry skips empty and collapsed shapes`() {
        assertNull(geometry(width = 0f))
        assertNull(geometry(spreadRadius = -5f))
    }

    @Test
    fun `box shadow geometry validates public drawing inputs`() {
        assertFailsWith<IllegalArgumentException> { geometry(width = -1f) }
        assertFailsWith<IllegalArgumentException> { geometry(blurRadius = -1f) }
        assertFailsWith<IllegalArgumentException> { geometry(offsetX = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { geometry(spreadRadius = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { geometry(cornerRadius = -1f) }
    }

    private fun geometry(
        width: Float = 10f,
        offsetX: Float = 0f,
        blurRadius: Float = 0f,
        spreadRadius: Float = 0f,
        cornerRadius: Float = 0f,
    ): Mine2DBoxShadowGeometry? = calculateBoxShadowGeometry(
        x = 0f,
        y = 0f,
        width = width,
        height = 10f,
        offsetX = offsetX,
        offsetY = 0f,
        blurRadius = blurRadius,
        spreadRadius = spreadRadius,
        cornerRadius = cornerRadius,
    )
}
