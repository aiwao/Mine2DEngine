package io.github.aiwao.mine2dengine

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class RoundedBorderSide {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT,
}

private data class RoundedBorderSample(
    val outerX: Float,
    val outerY: Float,
    val innerX: Float,
    val innerY: Float,
    val sideToNext: RoundedBorderSide,
)

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

/** Derives the padding-edge radii from normalized border-edge radii. */
internal fun Mine2DRoundedRectRadii.inset(
    top: Float,
    right: Float,
    bottom: Float,
    left: Float,
    innerWidth: Float,
    innerHeight: Float,
): Mine2DRoundedRectRadii {
    require(listOf(top, right, bottom, left).all { it.isFinite() && it >= 0f }) {
        "Rounded rectangle insets must be finite and non-negative"
    }
    return Mine2DRoundedRectRadii(
        topLeft = Mine2DCornerRadius(
            horizontal = max(0f, topLeft.horizontal - left),
            vertical = max(0f, topLeft.vertical - top),
        ).withoutDegenerateAxis(),
        topRight = Mine2DCornerRadius(
            horizontal = max(0f, topRight.horizontal - right),
            vertical = max(0f, topRight.vertical - top),
        ).withoutDegenerateAxis(),
        bottomRight = Mine2DCornerRadius(
            horizontal = max(0f, bottomRight.horizontal - right),
            vertical = max(0f, bottomRight.vertical - bottom),
        ).withoutDegenerateAxis(),
        bottomLeft = Mine2DCornerRadius(
            horizontal = max(0f, bottomLeft.horizontal - left),
            vertical = max(0f, bottomLeft.vertical - bottom),
        ).withoutDegenerateAxis(),
    ).normalized(innerWidth, innerHeight)
}

/** Builds a rounded border ring without filling its inner padding box. */
internal fun triangulateRoundedBorder(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    outerRadii: Mine2DRoundedRectRadii,
    topWidth: Float,
    rightWidth: Float,
    bottomWidth: Float,
    leftWidth: Float,
    topColor: Int,
    rightColor: Int,
    bottomColor: Int,
    leftColor: Int,
): TriangulatedPolygon? {
    require(x.isFinite() && y.isFinite()) { "Rounded border coordinates must be finite" }
    require(width.isFinite() && width >= 0f && height.isFinite() && height >= 0f) {
        "Rounded border dimensions must be finite and non-negative"
    }
    require(listOf(topWidth, rightWidth, bottomWidth, leftWidth).all {
        it.isFinite() && it >= 0f
    }) { "Rounded border widths must be finite and non-negative" }
    if (width == 0f || height == 0f) return null

    val top = topWidth.coerceAtMost(height)
    val bottom = bottomWidth.coerceAtMost((height - top).coerceAtLeast(0f))
    val left = leftWidth.coerceAtMost(width)
    val right = rightWidth.coerceAtMost((width - left).coerceAtLeast(0f))
    if (top == 0f && right == 0f && bottom == 0f && left == 0f) return null

    val usedOuter = outerRadii.normalized(width, height)
    val innerX = x + left
    val innerY = y + top
    val innerWidth = (width - left - right).coerceAtLeast(0f)
    val innerHeight = (height - top - bottom).coerceAtLeast(0f)
    val innerRadii = usedOuter.inset(
        top = top,
        right = right,
        bottom = bottom,
        left = left,
        innerWidth = innerWidth,
        innerHeight = innerHeight,
    )

    val widths = mapOf(
        RoundedBorderSide.TOP to top,
        RoundedBorderSide.RIGHT to right,
        RoundedBorderSide.BOTTOM to bottom,
        RoundedBorderSide.LEFT to left,
    )
    val colors = mapOf(
        RoundedBorderSide.TOP to topColor,
        RoundedBorderSide.RIGHT to rightColor,
        RoundedBorderSide.BOTTOM to bottomColor,
        RoundedBorderSide.LEFT to leftColor,
    )
    val samples = mutableListOf<RoundedBorderSample>()

    fun appendCorner(
        outerCenterX: Float,
        outerCenterY: Float,
        outerRadius: Mine2DCornerRadius,
        innerCenterX: Float,
        innerCenterY: Float,
        innerRadius: Mine2DCornerRadius,
        startAngle: Double,
        firstSide: RoundedBorderSide,
        secondSide: RoundedBorderSide,
    ) {
        val minimumSegments = max(2, cornerSegmentCount(outerRadius))
        val segments = if (minimumSegments % 2 == 0) minimumSegments else minimumSegments + 1
        for (step in 0..segments) {
            val angle = startAngle + (PI / 2.0) * step / segments
            val defaultSide = if (step * 2 < segments) firstSide else secondSide
            val adjacentSide = if (defaultSide == firstSide) secondSide else firstSide
            val side = if (widths.getValue(defaultSide) > 0f) defaultSide else adjacentSide
            samples += RoundedBorderSample(
                outerX = outerCenterX + cos(angle).toFloat() * outerRadius.horizontal,
                outerY = outerCenterY + sin(angle).toFloat() * outerRadius.vertical,
                innerX = innerCenterX + cos(angle).toFloat() * innerRadius.horizontal,
                innerY = innerCenterY + sin(angle).toFloat() * innerRadius.vertical,
                sideToNext = if (step == segments) secondSide else side,
            )
        }
    }

    appendCorner(
        x + width - usedOuter.topRight.horizontal,
        y + usedOuter.topRight.vertical,
        usedOuter.topRight,
        innerX + innerWidth - innerRadii.topRight.horizontal,
        innerY + innerRadii.topRight.vertical,
        innerRadii.topRight,
        -PI / 2.0,
        RoundedBorderSide.TOP,
        RoundedBorderSide.RIGHT,
    )
    appendCorner(
        x + width - usedOuter.bottomRight.horizontal,
        y + height - usedOuter.bottomRight.vertical,
        usedOuter.bottomRight,
        innerX + innerWidth - innerRadii.bottomRight.horizontal,
        innerY + innerHeight - innerRadii.bottomRight.vertical,
        innerRadii.bottomRight,
        0.0,
        RoundedBorderSide.RIGHT,
        RoundedBorderSide.BOTTOM,
    )
    appendCorner(
        x + usedOuter.bottomLeft.horizontal,
        y + height - usedOuter.bottomLeft.vertical,
        usedOuter.bottomLeft,
        innerX + innerRadii.bottomLeft.horizontal,
        innerY + innerHeight - innerRadii.bottomLeft.vertical,
        innerRadii.bottomLeft,
        PI / 2.0,
        RoundedBorderSide.BOTTOM,
        RoundedBorderSide.LEFT,
    )
    appendCorner(
        x + usedOuter.topLeft.horizontal,
        y + usedOuter.topLeft.vertical,
        usedOuter.topLeft,
        innerX + innerRadii.topLeft.horizontal,
        innerY + innerRadii.topLeft.vertical,
        innerRadii.topLeft,
        PI,
        RoundedBorderSide.LEFT,
        RoundedBorderSide.TOP,
    )

    val vertices = ArrayList<Mine2DVertex>(samples.size * 4)
    val indices = ArrayList<Int>(samples.size * 6)
    fun addTriangle(first: Int, second: Int, third: Int) {
        val a = vertices[first]
        val b = vertices[second]
        val c = vertices[third]
        val cross = (b.x - a.x).toDouble() * (c.y - a.y) -
            (b.y - a.y).toDouble() * (c.x - a.x)
        if (kotlin.math.abs(cross) <= 1.0e-6) return
        indices += first
        if (cross < 0.0) {
            indices += second
            indices += third
        } else {
            indices += third
            indices += second
        }
    }
    samples.indices.forEach { index ->
        val current = samples[index]
        val next = samples[(index + 1) % samples.size]
        val color = colors.getValue(current.sideToNext)
        if (color ushr 24 == 0) return@forEach
        val base = vertices.size
        vertices += Mine2DVertex(current.outerX, current.outerY, color)
        vertices += Mine2DVertex(next.outerX, next.outerY, color)
        vertices += Mine2DVertex(next.innerX, next.innerY, color)
        vertices += Mine2DVertex(current.innerX, current.innerY, color)
        addTriangle(base, base + 2, base + 1)
        addTriangle(base, base + 3, base + 2)
    }
    return if (indices.isEmpty()) null else TriangulatedPolygon(vertices, indices.toIntArray())
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
