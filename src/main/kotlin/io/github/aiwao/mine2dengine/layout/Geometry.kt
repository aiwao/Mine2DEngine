package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DCornerRadius
import io.github.aiwao.mine2dengine.Mine2DRoundedRectRadii

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

/** A border-box shape after its length-percentage corner radii have been resolved. */
internal data class UiRoundedBox(
    val bounds: UiRect,
    val radii: Mine2DRoundedRectRadii,
) {
    fun contains(x: Float, y: Float): Boolean {
        if (!bounds.contains(x, y)) return false

        fun insideCorner(
            centerX: Float,
            centerY: Float,
            radius: Mine2DCornerRadius,
        ): Boolean {
            if (radius.horizontal == 0f || radius.vertical == 0f) return true
            val normalizedX = (x - centerX) / radius.horizontal
            val normalizedY = (y - centerY) / radius.vertical
            return normalizedX * normalizedX + normalizedY * normalizedY <= 1f
        }

        val topLeft = radii.topLeft
        if (x < bounds.left + topLeft.horizontal && y < bounds.top + topLeft.vertical) {
            return insideCorner(
                bounds.left + topLeft.horizontal,
                bounds.top + topLeft.vertical,
                topLeft,
            )
        }

        val topRight = radii.topRight
        if (x >= bounds.right - topRight.horizontal && y < bounds.top + topRight.vertical) {
            return insideCorner(
                bounds.right - topRight.horizontal,
                bounds.top + topRight.vertical,
                topRight,
            )
        }

        val bottomRight = radii.bottomRight
        if (x >= bounds.right - bottomRight.horizontal && y >= bounds.bottom - bottomRight.vertical) {
            return insideCorner(
                bounds.right - bottomRight.horizontal,
                bounds.bottom - bottomRight.vertical,
                bottomRight,
            )
        }

        val bottomLeft = radii.bottomLeft
        if (x < bounds.left + bottomLeft.horizontal && y >= bounds.bottom - bottomLeft.vertical) {
            return insideCorner(
                bounds.left + bottomLeft.horizontal,
                bounds.bottom - bottomLeft.vertical,
                bottomLeft,
            )
        }
        return true
    }
}

internal fun UiBorderRadii.resolve(
    bounds: UiRect,
    lengthResolver: UiLengthResolver,
): UiRoundedBox {
    fun resolveCorner(value: UiCornerRadius): Mine2DCornerRadius = Mine2DCornerRadius(
        horizontal = checkNotNull(lengthResolver.resolve(value.horizontal, bounds.width)),
        vertical = checkNotNull(lengthResolver.resolve(value.vertical, bounds.height)),
    )
    return UiRoundedBox(
        bounds = bounds,
        radii = Mine2DRoundedRectRadii(
            topLeft = resolveCorner(topLeft),
            topRight = resolveCorner(topRight),
            bottomRight = resolveCorner(bottomRight),
            bottomLeft = resolveCorner(bottomLeft),
        ).normalized(bounds.width, bounds.height),
    )
}
