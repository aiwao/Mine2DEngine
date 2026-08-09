package io.github.aiwao.mine2dengine

import org.joml.Matrix3x2f
import org.joml.Vector2f
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

class TextPixelAlignmentTest {
    @Test
    fun `aligns a quarter-unit baseline at two physical pixels per GUI unit`() {
        val aligned = alignTextOriginYToPixelGrid(
            x = 12.25f,
            y = 17.25f,
            pose = Matrix3x2f(),
            pixelsPerGuiUnitY = 2f,
        )

        assertEquals(Vector2f(12.25f, 17f), aligned)
    }

    @Test
    fun `aligns only the transformed vertical origin to a physical pixel`() {
        val pose = Matrix3x2f()
            .translate(3.2f, 1.7f)
            .rotate(0.3f)
            .scale(1.25f, 0.75f)
        val originalScreen = pose.transformPosition(5.25f, 7.25f, Vector2f())

        val aligned = alignTextOriginYToPixelGrid(
            x = 5.25f,
            y = 7.25f,
            pose = pose,
            pixelsPerGuiUnitY = 2f,
        )
        val alignedScreen = pose.transformPosition(aligned, Vector2f())

        assertEquals(originalScreen.x, alignedScreen.x, absoluteTolerance = 0.0001f)
        assertEquals(
            round(originalScreen.y * 2f) / 2f,
            alignedScreen.y,
            absoluteTolerance = 0.0001f,
        )
    }

    @Test
    fun `returns the original origin for a singular pose`() {
        val aligned = alignTextOriginYToPixelGrid(
            x = 5.25f,
            y = 7.25f,
            pose = Matrix3x2f().scale(1f, 0f),
            pixelsPerGuiUnitY = 2f,
        )

        assertEquals(Vector2f(5.25f, 7.25f), aligned)
    }
}
