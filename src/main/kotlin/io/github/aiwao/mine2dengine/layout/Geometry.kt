package io.github.aiwao.mine2dengine.layout

/** Width and height in GUI coordinates. */
data class UiSize(
    val width: Float,
    val height: Float,
) {
    init {
        require(width.isFinite() && width >= 0f) { "Width must be finite and non-negative: $width" }
        require(height.isFinite() && height >= 0f) { "Height must be finite and non-negative: $height" }
    }
}

/** A rectangle whose [left] and [top] coordinates are inclusive. */
data class UiRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(left.isFinite()) { "Left must be finite: $left" }
        require(top.isFinite()) { "Top must be finite: $top" }
        require(width.isFinite() && width >= 0f) { "Width must be finite and non-negative: $width" }
        require(height.isFinite() && height >= 0f) { "Height must be finite and non-negative: $height" }
    }

    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height

    /** Uses half-open bounds, matching the pixels covered by a rendered rectangle. */
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

/** CSS-like edge values in top, right, bottom, left order. */
data class UiEdges(
    val top: Float,
    val right: Float,
    val bottom: Float,
    val left: Float,
) {
    constructor() : this(0f, 0f, 0f, 0f)

    constructor(all: Float) : this(all, all, all, all)

    constructor(vertical: Float, horizontal: Float) : this(
        top = vertical,
        right = horizontal,
        bottom = vertical,
        left = horizontal,
    )

    init {
        require(top.isFinite() && top >= 0f) { "Top edge must be finite and non-negative: $top" }
        require(right.isFinite() && right >= 0f) { "Right edge must be finite and non-negative: $right" }
        require(bottom.isFinite() && bottom >= 0f) { "Bottom edge must be finite and non-negative: $bottom" }
        require(left.isFinite() && left >= 0f) { "Left edge must be finite and non-negative: $left" }
    }

    val horizontal: Float
        get() = left + right

    val vertical: Float
        get() = top + bottom
}
