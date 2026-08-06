package io.github.aiwao.mine2dengine

import kotlin.math.abs

internal data class TriangulatedPolygon(
    val vertices: List<Mine2DVertex>,
    val indices: IntArray,
)

/** Ear-clipping triangulation for simple convex or concave polygons. */
internal object PolygonTriangulator {
    private const val EPSILON = 1.0e-6

    fun triangulate(input: Iterable<Mine2DVertex>): TriangulatedPolygon {
        val vertices = normalize(input)
        require(vertices.size >= 3) { "A polygon requires at least three distinct vertices" }
        require(!hasSelfIntersection(vertices)) { "A polygon must not intersect itself" }

        val area = signedArea(vertices)
        require(abs(area) > EPSILON) { "A polygon must have a non-zero area" }

        val inputIsCounterClockwise = area > 0.0
        val remaining = vertices.indices.toMutableList()
        val indices = ArrayList<Int>((vertices.size - 2) * 3)

        while (remaining.size > 3) {
            var clippedEar = false

            for (cursor in remaining.indices) {
                val previous = remaining[(cursor - 1 + remaining.size) % remaining.size]
                val current = remaining[cursor]
                val next = remaining[(cursor + 1) % remaining.size]

                if (!isConvex(vertices[previous], vertices[current], vertices[next], inputIsCounterClockwise)) {
                    continue
                }

                val containsAnotherVertex = remaining.any { candidate ->
                    candidate != previous &&
                        candidate != current &&
                        candidate != next &&
                        pointInTriangle(
                            vertices[candidate],
                            vertices[previous],
                            vertices[current],
                            vertices[next],
                        )
                }
                if (containsAnotherVertex) continue

                addClockwiseTriangle(indices, vertices, previous, current, next)
                remaining.removeAt(cursor)
                clippedEar = true
                break
            }

            require(clippedEar) {
                "The polygon could not be triangulated; check for overlapping edges or invalid vertices"
            }
        }

        addClockwiseTriangle(indices, vertices, remaining[0], remaining[1], remaining[2])
        return TriangulatedPolygon(vertices, indices.toIntArray())
    }

    private fun normalize(input: Iterable<Mine2DVertex>): List<Mine2DVertex> {
        val vertices = ArrayList<Mine2DVertex>()
        for (vertex in input) {
            require(vertex.x.isFinite() && vertex.y.isFinite()) { "Polygon coordinates must be finite" }
            if (vertices.isEmpty() || !samePosition(vertices.last(), vertex)) {
                vertices += vertex
            }
        }

        if (vertices.size > 1 && samePosition(vertices.first(), vertices.last())) {
            vertices.removeLast()
        }

        var changed: Boolean
        do {
            changed = false
            if (vertices.size <= 3) break

            for (index in vertices.indices) {
                val previous = vertices[(index - 1 + vertices.size) % vertices.size]
                val current = vertices[index]
                val next = vertices[(index + 1) % vertices.size]
                if (isRedundantCollinearVertex(previous, current, next)) {
                    vertices.removeAt(index)
                    changed = true
                    break
                }
            }
        } while (changed)

        return vertices.toList()
    }

    private fun signedArea(vertices: List<Mine2DVertex>): Double {
        var twiceArea = 0.0
        for (index in vertices.indices) {
            val current = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            twiceArea += current.x.toDouble() * next.y - next.x.toDouble() * current.y
        }
        return twiceArea / 2.0
    }

    private fun isConvex(
        previous: Mine2DVertex,
        current: Mine2DVertex,
        next: Mine2DVertex,
        counterClockwise: Boolean,
    ): Boolean {
        val cross = cross(previous, current, next)
        return if (counterClockwise) cross > EPSILON else cross < -EPSILON
    }

    private fun pointInTriangle(
        point: Mine2DVertex,
        a: Mine2DVertex,
        b: Mine2DVertex,
        c: Mine2DVertex,
    ): Boolean {
        val ab = cross(a, b, point)
        val bc = cross(b, c, point)
        val ca = cross(c, a, point)
        val hasNegative = ab < -EPSILON || bc < -EPSILON || ca < -EPSILON
        val hasPositive = ab > EPSILON || bc > EPSILON || ca > EPSILON
        return !(hasNegative && hasPositive)
    }

    /** GUI quads use clockwise screen-space winding, so polygons do the same. */
    private fun addClockwiseTriangle(
        output: MutableList<Int>,
        vertices: List<Mine2DVertex>,
        a: Int,
        b: Int,
        c: Int,
    ) {
        if (cross(vertices[a], vertices[b], vertices[c]) < 0.0) {
            output += a
            output += b
            output += c
        } else {
            output += c
            output += b
            output += a
        }
    }

    private fun hasSelfIntersection(vertices: List<Mine2DVertex>): Boolean {
        for (first in vertices.indices) {
            val firstNext = (first + 1) % vertices.size
            for (second in first + 1 until vertices.size) {
                val secondNext = (second + 1) % vertices.size
                val adjacent = first == second ||
                    firstNext == second ||
                    secondNext == first
                if (adjacent) continue

                if (segmentsIntersect(
                        vertices[first],
                        vertices[firstNext],
                        vertices[second],
                        vertices[secondNext],
                    )
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun segmentsIntersect(
        a: Mine2DVertex,
        b: Mine2DVertex,
        c: Mine2DVertex,
        d: Mine2DVertex,
    ): Boolean {
        val abC = cross(a, b, c)
        val abD = cross(a, b, d)
        val cdA = cross(c, d, a)
        val cdB = cross(c, d, b)

        if (((abC > EPSILON && abD < -EPSILON) || (abC < -EPSILON && abD > EPSILON)) &&
            ((cdA > EPSILON && cdB < -EPSILON) || (cdA < -EPSILON && cdB > EPSILON))
        ) {
            return true
        }

        return (abs(abC) <= EPSILON && onSegment(a, b, c)) ||
            (abs(abD) <= EPSILON && onSegment(a, b, d)) ||
            (abs(cdA) <= EPSILON && onSegment(c, d, a)) ||
            (abs(cdB) <= EPSILON && onSegment(c, d, b))
    }

    private fun onSegment(a: Mine2DVertex, b: Mine2DVertex, point: Mine2DVertex): Boolean =
        point.x.toDouble() >= minOf(a.x, b.x) - EPSILON &&
            point.x.toDouble() <= maxOf(a.x, b.x) + EPSILON &&
            point.y.toDouble() >= minOf(a.y, b.y) - EPSILON &&
            point.y.toDouble() <= maxOf(a.y, b.y) + EPSILON

    private fun isRedundantCollinearVertex(
        previous: Mine2DVertex,
        current: Mine2DVertex,
        next: Mine2DVertex,
    ): Boolean {
        if (abs(cross(previous, current, next)) > EPSILON) return false
        val first = (current.x - previous.x).toDouble() * (current.x - next.x) +
            (current.y - previous.y).toDouble() * (current.y - next.y)
        return first <= EPSILON
    }

    private fun samePosition(a: Mine2DVertex, b: Mine2DVertex): Boolean =
        abs(a.x.toDouble() - b.x) <= EPSILON && abs(a.y.toDouble() - b.y) <= EPSILON

    private fun cross(a: Mine2DVertex, b: Mine2DVertex, c: Mine2DVertex): Double =
        (b.x - a.x).toDouble() * (c.y - a.y) - (b.y - a.y).toDouble() * (c.x - a.x)
}
