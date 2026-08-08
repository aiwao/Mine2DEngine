package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont

/** The direction in which a container lays out its direct children. */
enum class UiDirection {
    VERTICAL,
    HORIZONTAL,
}

/** Horizontal alignment inside the content area of an element. */
enum class UiHorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/** Vertical alignment inside the content area of an element. */
enum class UiVerticalAlignment {
    TOP,
    CENTER,
    BOTTOM,
}

/** Determines whether an explicit size applies to the content or the padded box. */
enum class UiBoxSizing {
    CONTENT_BOX,
    BORDER_BOX,
}

/**
 * Visual and layout properties shared by every UI element.
 *
 * [boxSizing] determines whether a non-null [width] or [height] describes the content box or the
 * complete padded box. Padding is painted inside the background, while margin remains outside it,
 * following the CSS box model.
 * [gap] adds space between adjacent direct children without adding space at the
 * edges of the content box.
 * [noneDisplay] is evaluated during layout, rendering, and pointer queries.
 * When its value changes, geometry is recalculated. A true value removes the
 * element and its descendants from layout, rendering, and pointer input, like
 * CSS `display: none`.
 * [horizontalAlignment] and [verticalAlignment] position direct children in a
 * container and text inside a paragraph's content box.
 * A null size shrinks to the element's text or children. [color], [font], and
 * [dropShadow] are inherited by descendants when null. At the root, color defaults
 * to opaque white and drop shadow defaults to enabled. Every text element must
 * resolve a font from itself or an ancestor.
 */
data class UiStyle(
    val color: Int? = null,
    val backgroundColor: Int? = null,
    val margin: UiEdges = UiEdges(),
    val padding: UiEdges = UiEdges(),
    val direction: UiDirection = UiDirection.VERTICAL,
    val horizontalAlignment: UiHorizontalAlignment = UiHorizontalAlignment.LEFT,
    val verticalAlignment: UiVerticalAlignment = UiVerticalAlignment.TOP,
    val width: Float? = null,
    val height: Float? = null,
    val font: Mine2DFont? = null,
    val dropShadow: Boolean? = null,
    val gap: Float = 0f,
    val boxSizing: UiBoxSizing = UiBoxSizing.CONTENT_BOX,
    val noneDisplay: () -> Boolean = { false },
) {
    companion object {
        const val DEFAULT_COLOR: Int = -1
        const val DEFAULT_DROP_SHADOW: Boolean = true
    }

    init {
        require(width == null || width.isFinite() && width >= 0f) {
            "Width must be null or finite and non-negative: $width"
        }
        require(height == null || height.isFinite() && height >= 0f) {
            "Height must be null or finite and non-negative: $height"
        }
        require(gap.isFinite() && gap >= 0f) {
            "Gap must be finite and non-negative: $gap"
        }
    }
}

/** Text properties after resolving inheritance from ancestor styles. */
internal data class ResolvedUiTextStyle(
    val color: Int = UiStyle.DEFAULT_COLOR,
    val font: Mine2DFont? = null,
    val dropShadow: Boolean = UiStyle.DEFAULT_DROP_SHADOW,
)

internal fun UiStyle.resolveTextStyle(parent: ResolvedUiTextStyle): ResolvedUiTextStyle =
    ResolvedUiTextStyle(
        color = color ?: parent.color,
        font = font ?: parent.font,
        dropShadow = dropShadow ?: parent.dropShadow,
    )
