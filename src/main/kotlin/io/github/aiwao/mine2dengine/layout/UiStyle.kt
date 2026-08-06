package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont

/** The direction in which a div lays out its direct children. */
enum class UiDirection {
    VERTICAL,
    HORIZONTAL,
}

/** Horizontal alignment inside the content area of an element. */
enum class UiAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/**
 * Visual and layout properties shared by every UI element.
 *
 * [width] and [height] describe the content box. Padding is painted inside the
 * background, while margin remains outside it, following the CSS box model.
 * A null size shrinks to the element's text or children. [font] is inherited by
 * descendants; every text element must resolve a font from itself or an ancestor.
 */
data class UiStyle(
    val color: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int? = null,
    val margin: UiEdges = UiEdges(),
    val padding: UiEdges = UiEdges(),
    val direction: UiDirection = UiDirection.VERTICAL,
    val alignment: UiAlignment = UiAlignment.LEFT,
    val width: Float? = null,
    val height: Float? = null,
    val font: Mine2DFont? = null,
) {
    init {
        require(width == null || width.isFinite() && width >= 0f) {
            "Width must be null or finite and non-negative: $width"
        }
        require(height == null || height.isFinite() && height >= 0f) {
            "Height must be null or finite and non-negative: $height"
        }
    }
}
