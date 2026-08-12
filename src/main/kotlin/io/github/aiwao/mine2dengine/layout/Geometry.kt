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
