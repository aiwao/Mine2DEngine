package io.github.aiwao.mine2dengine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolygonTriangulatorTest {
    @Test
    fun `triangulates a convex polygon with GUI winding`() {
        val result = PolygonTriangulator.triangulate(
            listOf(
                vertex(0, 0),
                vertex(4, 0),
                vertex(4, 3),
                vertex(0, 3),
            ),
        )

        assertEquals(6, result.indices.size)
        assertEquals(12.0, triangleArea(result), 1.0e-6)
        assertClockwise(result)
    }

    @Test
    fun `triangulates a concave polygon`() {
        val result = PolygonTriangulator.triangulate(
            listOf(
                vertex(0, 0),
                vertex(4, 0),
                vertex(4, 4),
                vertex(2, 2),
                vertex(0, 4),
            ),
        )

        assertEquals(9, result.indices.size)
        assertEquals(12.0, triangleArea(result), 1.0e-6)
        assertClockwise(result)
    }

    @Test
    fun `accepts clockwise input and a repeated closing vertex`() {
        val result = PolygonTriangulator.triangulate(
            listOf(
                vertex(0, 0),
                vertex(0, 2),
                vertex(3, 2),
                vertex(3, 0),
                vertex(0, 0),
            ),
        )

        assertEquals(4, result.vertices.size)
        assertEquals(6.0, triangleArea(result), 1.0e-6)
        assertClockwise(result)
    }

    @Test
    fun `removes redundant collinear vertices`() {
        val result = PolygonTriangulator.triangulate(
            listOf(
                vertex(0, 0),
                vertex(2, 0),
                vertex(4, 0),
                vertex(4, 2),
                vertex(0, 2),
            ),
        )

        assertEquals(4, result.vertices.size)
        assertEquals(8.0, triangleArea(result), 1.0e-6)
    }

    @Test
    fun `rejects self-intersecting polygons`() {
        assertFailsWith<IllegalArgumentException> {
            PolygonTriangulator.triangulate(
                listOf(
                    vertex(0, 0),
                    vertex(4, 4),
                    vertex(0, 4),
                    vertex(4, 0),
                ),
            )
        }
    }

    @Test
    fun `rejects non-finite coordinates`() {
        assertFailsWith<IllegalArgumentException> {
            PolygonTriangulator.triangulate(
                listOf(
                    Mine2DVertex(Float.NaN, 0f),
                    vertex(1, 0),
                    vertex(0, 1),
                ),
            )
        }
    }

    private fun assertClockwise(polygon: TriangulatedPolygon) {
        polygon.indices.asList().chunked(3).forEach { triangle ->
            val a = polygon.vertices[triangle[0]]
            val b = polygon.vertices[triangle[1]]
            val c = polygon.vertices[triangle[2]]
            assertTrue(cross(a, b, c) < 0.0, "Triangle must use clockwise screen-space winding")
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
        (b.x - a.x).toDouble() * (c.y - a.y) - (b.y - a.y).toDouble() * (c.x - a.x)

    private fun vertex(x: Int, y: Int) = Mine2DVertex(x.toFloat(), y.toFloat())
}
