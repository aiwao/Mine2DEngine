package io.github.aiwao.mine2dengine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundedRectTest {
    @Test
    fun `zero radii use rectangular geometry`() {
        val polygon = geometry(radii = Mine2DRoundedRectRadii.ZERO)!!

        assertEquals(4, polygon.vertices.size)
        assertEquals(6, polygon.indices.size)
        assertEquals(listOf(0, 2, 1, 0, 3, 2), polygon.indices.asList())
        assertEquals(4000.0, triangleArea(polygon), 1.0e-6)
        assertClockwise(polygon)
    }

    @Test
    fun `rounded geometry approximates each elliptical corner with clockwise triangles`() {
        val polygon = geometry(radii = Mine2DRoundedRectRadii(10f))!!
        val expectedArea = 100.0 * 40.0 - 4.0 * 10.0 * 10.0 + PI * 10.0 * 10.0

        assertTrue(polygon.vertices.size > 4)
        assertEquals((polygon.vertices.size - 2) * 3, polygon.indices.size)
        assertEquals(expectedArea, triangleArea(polygon), 10.0)
        assertTrue(polygon.vertices.all { vertex ->
            vertex.x in 0f..100f && vertex.y in 0f..40f
        })
        assertClockwise(polygon)
    }

    @Test
    fun `overlapping public radii are reduced with one CSS scale factor`() {
        val radii = Mine2DRoundedRectRadii(
            topLeft = Mine2DCornerRadius(80f, 10f),
            topRight = Mine2DCornerRadius(80f, 10f),
            bottomRight = Mine2DCornerRadius(5f, 30f),
            bottomLeft = Mine2DCornerRadius(5f, 30f),
        ).normalized(width = 100f, height = 40f)

        assertEquals(50f, radii.topLeft.horizontal)
        assertEquals(6.25f, radii.topLeft.vertical)
        assertEquals(3.125f, radii.bottomRight.horizontal)
        assertEquals(18.75f, radii.bottomRight.vertical)
    }

    @Test
    fun `touching elliptical corners form a closed non-degenerate mesh`() {
        val polygon = geometry(radii = Mine2DRoundedRectRadii(50f, 20f))!!

        assertEquals(PI * 50.0 * 20.0, triangleArea(polygon), 35.0)
        assertClockwise(polygon)
    }

    @Test
    fun `degenerate radii and empty rectangles are handled without invalid triangles`() {
        val radii = Mine2DRoundedRectRadii(
            topLeft = Mine2DCornerRadius(10f, 0f),
            topRight = Mine2DCornerRadius(10f),
        )

        assertNull(geometry(width = 0f, radii = radii))
        assertEquals(Mine2DCornerRadius.ZERO, radii.normalized(100f, 40f).topLeft)
        assertFailsWith<IllegalArgumentException> { Mine2DCornerRadius(-1f) }
        assertFailsWith<IllegalArgumentException> {
            geometry(width = -1f, radii = Mine2DRoundedRectRadii.ZERO)
        }
    }

    @Test
    fun `rectangular border geometry is a hollow clockwise ring`() {
        val polygon = borderGeometry(radii = Mine2DRoundedRectRadii.ZERO)!!

        assertEquals(1300.0, triangleArea(polygon), 1.0e-6)
        assertClockwise(polygon)
        assertTrue(polygon.indices.asList().chunked(3).none { triangle ->
            pointInTriangle(50f, 20f, triangle.map(polygon.vertices::get))
        })
    }

    @Test
    fun `rounded border subtracts its inner padding edge and retains side colors`() {
        val colors = listOf(
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFFFFFFFF.toInt(),
        )
        val polygon = borderGeometry(
            radii = Mine2DRoundedRectRadii(10f),
            colors = colors,
        )!!
        val outerArea = 100.0 * 40.0 - 4.0 * 10.0 * 10.0 + PI * 10.0 * 10.0
        val innerArea = 90.0 * 30.0 - 4.0 * 5.0 * 5.0 + PI * 5.0 * 5.0

        assertEquals(outerArea - innerArea, triangleArea(polygon), 10.0)
        assertEquals(colors.toSet(), polygon.vertices.map(Mine2DVertex::color).toSet())
        assertClockwise(polygon)
    }

    @Test
    fun `padding edge radii subtract their adjacent asymmetric borders`() {
        val inner = Mine2DRoundedRectRadii(
            topLeft = Mine2DCornerRadius(20f, 18f),
            topRight = Mine2DCornerRadius(16f, 14f),
            bottomRight = Mine2DCornerRadius(12f, 10f),
            bottomLeft = Mine2DCornerRadius(8f, 6f),
        ).inset(
            top = 2f,
            right = 3f,
            bottom = 4f,
            left = 5f,
            innerWidth = 92f,
            innerHeight = 34f,
        )

        assertEquals(Mine2DCornerRadius(15f, 16f), inner.topLeft)
        assertEquals(Mine2DCornerRadius(13f, 12f), inner.topRight)
        assertEquals(Mine2DCornerRadius(9f, 6f), inner.bottomRight)
        assertEquals(Mine2DCornerRadius(3f, 2f), inner.bottomLeft)
    }

    private fun geometry(
        width: Float = 100f,
        radii: Mine2DRoundedRectRadii,
    ): TriangulatedPolygon? = triangulateRoundedRect(
        x = 0f,
        y = 0f,
        width = width,
        height = 40f,
        radii = radii,
        color = -1,
    )

    private fun borderGeometry(
        radii: Mine2DRoundedRectRadii,
        colors: List<Int> = List(4) { -1 },
    ): TriangulatedPolygon? = triangulateRoundedBorder(
        x = 0f,
        y = 0f,
        width = 100f,
        height = 40f,
        outerRadii = radii,
        topWidth = 5f,
        rightWidth = 5f,
        bottomWidth = 5f,
        leftWidth = 5f,
        topColor = colors[0],
        rightColor = colors[1],
        bottomColor = colors[2],
        leftColor = colors[3],
    )

    private fun assertClockwise(polygon: TriangulatedPolygon) {
        polygon.indices.asList().chunked(3).forEach { triangle ->
            val cross = cross(
                polygon.vertices[triangle[0]],
                polygon.vertices[triangle[1]],
                polygon.vertices[triangle[2]],
            )
            assertTrue(cross < 0.0, "Triangle must be non-degenerate and GUI-clockwise: $triangle")
        }
    }

    private fun triangleArea(polygon: TriangulatedPolygon): Double =
        polygon.indices.asList().chunked(3).sumOf { triangle ->
            abs(
                cross(
                    polygon.vertices[triangle[0]],
                    polygon.vertices[triangle[1]],
                    polygon.vertices[triangle[2]],
                ),
            ) / 2.0
        }

    private fun cross(a: Mine2DVertex, b: Mine2DVertex, c: Mine2DVertex): Double =
        (b.x - a.x).toDouble() * (c.y - a.y) -
            (b.y - a.y).toDouble() * (c.x - a.x)

    private fun pointInTriangle(
        x: Float,
        y: Float,
        triangle: List<Mine2DVertex>,
    ): Boolean {
        val point = Mine2DVertex(x, y, -1)
        val crosses = listOf(
            cross(triangle[0], triangle[1], point),
            cross(triangle[1], triangle[2], point),
            cross(triangle[2], triangle[0], point),
        )
        return crosses.all { it <= 0.0 } || crosses.all { it >= 0.0 }
    }
}
