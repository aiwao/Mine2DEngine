package io.github.aiwao.mine2dengine

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Horizontal and vertical radii of one rounded-rectangle corner. */
data class Mine2DCornerRadius(
    val horizontal: Float,
    val vertical: Float = horizontal,
) {
    init {
        require(horizontal.isFinite() && horizontal >= 0f) {
            "A horizontal corner radius must be finite and non-negative: $horizontal"
        }
        require(vertical.isFinite() && vertical >= 0f) {
            "A vertical corner radius must be finite and non-negative: $vertical"
        }
    }

    internal fun scaled(scale: Float): Mine2DCornerRadius =
        Mine2DCornerRadius(horizontal * scale, vertical * scale)

    internal fun outset(amount: Float): Mine2DCornerRadius = Mine2DCornerRadius(
        horizontal = max(0f, horizontal + amount),
        vertical = max(0f, vertical + amount),
    ).withoutDegenerateAxis()

    internal fun withoutDegenerateAxis(): Mine2DCornerRadius =
        if (horizontal == 0f || vertical == 0f) ZERO else this

    companion object {
        @JvmField
        val ZERO = Mine2DCornerRadius(0f)
    }
}

/** Physical corner radii of a rounded rectangle. */
data class Mine2DRoundedRectRadii(
    val topLeft: Mine2DCornerRadius = Mine2DCornerRadius.ZERO,
    val topRight: Mine2DCornerRadius = Mine2DCornerRadius.ZERO,
    val bottomRight: Mine2DCornerRadius = Mine2DCornerRadius.ZERO,
    val bottomLeft: Mine2DCornerRadius = Mine2DCornerRadius.ZERO,
) {
    /** Creates four equal circular corner radii. */
    constructor(all: Float) : this(Mine2DCornerRadius(all))

    /** Creates four equal elliptical corner radii. */
    constructor(horizontal: Float, vertical: Float) : this(
        Mine2DCornerRadius(horizontal, vertical),
    )

    /** Creates four equal corner radii. */
    constructor(all: Mine2DCornerRadius) : this(all, all, all, all)

    internal val isZero: Boolean
        get() = topLeft == Mine2DCornerRadius.ZERO &&
            topRight == Mine2DCornerRadius.ZERO &&
            bottomRight == Mine2DCornerRadius.ZERO &&
            bottomLeft == Mine2DCornerRadius.ZERO

    /** Applies the CSS overlapping-curves reduction to the supplied rectangle dimensions. */
    internal fun normalized(width: Float, height: Float): Mine2DRoundedRectRadii {
        require(width.isFinite() && width >= 0f && height.isFinite() && height >= 0f) {
            "Rounded rectangle dimensions must be finite and non-negative"
        }
        val radii = copy(
            topLeft = topLeft.withoutDegenerateAxis(),
            topRight = topRight.withoutDegenerateAxis(),
            bottomRight = bottomRight.withoutDegenerateAxis(),
            bottomLeft = bottomLeft.withoutDegenerateAxis(),
        )
        var scale = 1f
        scale = reduceScale(scale, width, radii.topLeft.horizontal + radii.topRight.horizontal)
        scale = reduceScale(scale, width, radii.bottomLeft.horizontal + radii.bottomRight.horizontal)
        scale = reduceScale(scale, height, radii.topLeft.vertical + radii.bottomLeft.vertical)
        scale = reduceScale(scale, height, radii.topRight.vertical + radii.bottomRight.vertical)
        return if (scale < 1f) radii.scaled(scale) else radii
    }

    internal fun outset(
        amount: Float,
        width: Float,
        height: Float,
    ): Mine2DRoundedRectRadii {
        require(amount.isFinite()) { "A rounded rectangle outset must be finite: $amount" }
        return Mine2DRoundedRectRadii(
            topLeft = topLeft.outset(amount),
            topRight = topRight.outset(amount),
            bottomRight = bottomRight.outset(amount),
            bottomLeft = bottomLeft.outset(amount),
        ).normalized(width, height)
    }

    private fun scaled(scale: Float): Mine2DRoundedRectRadii = Mine2DRoundedRectRadii(
        topLeft = topLeft.scaled(scale),
        topRight = topRight.scaled(scale),
        bottomRight = bottomRight.scaled(scale),
        bottomLeft = bottomLeft.scaled(scale),
    )

    companion object {
        @JvmField
        val ZERO = Mine2DRoundedRectRadii()

        private fun reduceScale(current: Float, edgeLength: Float, radiusSum: Float): Float =
            if (radiusSum > 0f) min(current, edgeLength / radiusSum) else current
    }
}

/** Builds an adaptively tessellated convex rounded rectangle with GUI-clockwise triangles. */
internal fun triangulateRoundedRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radii: Mine2DRoundedRectRadii,
    color: Int,
): TriangulatedPolygon? {
    require(x.isFinite() && y.isFinite()) { "Rounded rectangle coordinates must be finite" }
    require(width.isFinite() && width >= 0f && height.isFinite() && height >= 0f) {
        "Rounded rectangle dimensions must be finite and non-negative"
    }
    if (width == 0f || height == 0f) return null

    val used = radii.normalized(width, height)
    if (used.isZero) {
        return TriangulatedPolygon(
            vertices = listOf(
                Mine2DVertex(x, y, color),
                Mine2DVertex(x + width, y, color),
                Mine2DVertex(x + width, y + height, color),
                Mine2DVertex(x, y + height, color),
            ),
            indices = intArrayOf(0, 2, 1, 0, 3, 2),
        )
    }

    val vertices = mutableListOf<Mine2DVertex>()
    appendCorner(
        vertices,
        centerX = x + width - used.topRight.horizontal,
        centerY = y + used.topRight.vertical,
        radius = used.topRight,
        startAngle = -PI / 2.0,
        color = color,
    )
    appendCorner(
        vertices,
        centerX = x + width - used.bottomRight.horizontal,
        centerY = y + height - used.bottomRight.vertical,
        radius = used.bottomRight,
        startAngle = 0.0,
        color = color,
    )
    appendCorner(
        vertices,
        centerX = x + used.bottomLeft.horizontal,
        centerY = y + height - used.bottomLeft.vertical,
        radius = used.bottomLeft,
        startAngle = PI / 2.0,
        color = color,
    )
    appendCorner(
        vertices,
        centerX = x + used.topLeft.horizontal,
        centerY = y + used.topLeft.vertical,
        radius = used.topLeft,
        startAngle = PI,
        color = color,
    )
    if (vertices.size > 1 && samePosition(vertices.first(), vertices.last())) {
        vertices.removeLast()
    }

    val indices = IntArray((vertices.size - 2) * 3)
    var output = 0
    for (index in 1 until vertices.lastIndex) {
        // The perimeter follows screen-space clockwise order, whose mathematical cross product is
        // positive. Reverse each triangle for Minecraft's GUI-clockwise winding.
        indices[output++] = 0
        indices[output++] = index + 1
        indices[output++] = index
    }
    return TriangulatedPolygon(vertices.toList(), indices)
}

private fun appendCorner(
    output: MutableList<Mine2DVertex>,
    centerX: Float,
    centerY: Float,
    radius: Mine2DCornerRadius,
    startAngle: Double,
    color: Int,
) {
    if (radius == Mine2DCornerRadius.ZERO) {
        appendUnique(output, Mine2DVertex(centerX, centerY, color))
        return
    }

    val segments = cornerSegmentCount(radius)
    for (step in 0..segments) {
        val angle = startAngle + (PI / 2.0) * step / segments
        appendUnique(
            output,
            Mine2DVertex(
                x = centerX + cos(angle).toFloat() * radius.horizontal,
                y = centerY + sin(angle).toFloat() * radius.vertical,
                color = color,
            ),
        )
    }
}

private fun cornerSegmentCount(radius: Mine2DCornerRadius): Int {
    val maximumRadius = max(radius.horizontal, radius.vertical).toDouble()
    if (maximumRadius <= CURVE_TOLERANCE) return 1
    val maximumAngle = 2.0 * acos((1.0 - CURVE_TOLERANCE / maximumRadius).coerceIn(-1.0, 1.0))
    return ceil((PI / 2.0) / maximumAngle).toInt().coerceIn(1, MAX_CORNER_SEGMENTS)
}

private fun appendUnique(output: MutableList<Mine2DVertex>, vertex: Mine2DVertex) {
    if (output.lastOrNull()?.let { samePosition(it, vertex) } != true) output += vertex
}

private fun samePosition(first: Mine2DVertex, second: Mine2DVertex): Boolean =
    first.x == second.x && first.y == second.y

private const val CURVE_TOLERANCE = 0.25
private const val MAX_CORNER_SEGMENTS = 32
