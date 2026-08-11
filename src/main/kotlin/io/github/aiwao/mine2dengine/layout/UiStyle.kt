package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont
import io.github.aiwao.mine2dengine.Mine2DMaterial

private val DEFAULT_NONE_DISPLAY: () -> Boolean = { false }

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

/** Determines whether an element participates in normal flow and how its offsets are applied. */
enum class UiPosition {
    STATIC,
    RELATIVE,
    ABSOLUTE,
}

/** Unit used by CSS-like [UiStyle.width] and [UiStyle.height] values. */
enum class UiLengthUnit {
    PX,
    PERCENT,
}

/** A non-negative, finite CSS-like length used by [UiStyle.width] and [UiStyle.height]. */
data class UiLength(
    val value: Float,
    val unit: UiLengthUnit,
) {
    init {
        require(value.isFinite() && value >= 0f) {
            "Length must be finite and non-negative: $value"
        }
    }
}

/** Treats this value as a pixel length. */
val Float.px: UiLength
    get() = UiLength(this, UiLengthUnit.PX)

/** Treats this value as a percentage of the corresponding containing-block dimension. */
val Float.percent: UiLength
    get() = UiLength(this, UiLengthUnit.PERCENT)

internal fun UiLength.resolve(percentageBase: Float?): Float? {
    val resolved = when (unit) {
        UiLengthUnit.PX -> value
        UiLengthUnit.PERCENT -> percentageBase?.let { it * (value / 100f) }
    }
    require(resolved == null || resolved.isFinite()) {
        "Resolved length must be finite: $resolved"
    }
    return resolved
}

/**
 * Visual and layout properties shared by every UI element.
 *
 * Every nullable property uses null to mean that the property is unspecified. This allows a
 * declaration to distinguish an omitted property from an explicitly supplied default value such
 * as zero padding, a vertical direction, or static positioning. Unspecified properties first use
 * matching style-sheet declarations, then resolve to their CSS-like initial values during layout.
 *
 * [width] and [height] accept pixel and percentage [UiLength] values through [Float.px] and
 * [Float.percent]. Percentages use the corresponding resolved containing-block dimension, which is
 * normally the matching content dimension of the parent. [boxSizing] determines whether a non-null
 * [width] or [height] describes the content box or the complete padded box. Padding is painted
 * inside the background, while margin remains outside it, following the CSS box model.
 * [position] follows CSS static, relative, and absolute positioning. [left], [top], [right], and
 * [bottom] are nullable so that null represents CSS `auto`. Relative elements keep their normal
 * flow space, while absolute elements do not contribute to their container's size or gap. The
 * root's outer position is always controlled by [LayoutEngine.layout].
 * [gap] adds space between adjacent direct children and generated pseudo-element boxes without
 * adding space at the edges of the content box.
 * [noneDisplay] is evaluated during layout, rendering, and pointer queries.
 * When its value changes, geometry is recalculated. A true value removes the
 * element and its descendants from layout, rendering, and pointer input, like
 * CSS `display: none`.
 * [horizontalAlignment] and [verticalAlignment] position direct children in a
 * container and text inside a paragraph's content box.
 * A non-null [backgroundColor] paints only this element's bounds and is not inherited. The draw
 * uses [backgroundMaterial], or the renderer's current material when it is null. A
 * [backgroundMaterial] without a [backgroundColor] does not draw a background.
 * [boxShadow] paints behind this element and is not inherited or included in layout and hit bounds.
 * [dropShadow] filters the composited pixels of this element and its descendants. It is not
 * inherited or included in layout and hit bounds.
 * A null size shrinks to the element's text or children. [color], [font], and [textShadow] are
 * inherited by descendants when null. At the root, color defaults to opaque white and text shadow
 * defaults to none. Every text element must resolve a font from itself or an ancestor. Use
 * [UiTextShadow.NONE] to explicitly clear an inherited text shadow.
 */
data class UiStyle(
    val color: Int? = null,
    val backgroundColor: Int? = null,
    val backgroundMaterial: Mine2DMaterial? = null,
    val margin: UiEdges? = null,
    val padding: UiEdges? = null,
    val direction: UiDirection? = null,
    val horizontalAlignment: UiHorizontalAlignment? = null,
    val verticalAlignment: UiVerticalAlignment? = null,
    val width: UiLength? = null,
    val height: UiLength? = null,
    val position: UiPosition? = null,
    val left: Float? = null,
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    val font: Mine2DFont? = null,
    val gap: Float? = null,
    val boxSizing: UiBoxSizing? = null,
    val noneDisplay: (() -> Boolean)? = null,
    val boxShadow: UiBoxShadow? = null,
    val textShadow: UiTextShadow? = null,
    val dropShadow: UiDropShadow? = null,
) {
    companion object {
        const val DEFAULT_COLOR: Int = -1
    }

    init {
        require(left == null || left.isFinite()) {
            "Left must be null or finite: $left"
        }
        require(top == null || top.isFinite()) {
            "Top must be null or finite: $top"
        }
        require(right == null || right.isFinite()) {
            "Right must be null or finite: $right"
        }
        require(bottom == null || bottom.isFinite()) {
            "Bottom must be null or finite: $bottom"
        }
        require(gap == null || gap.isFinite() && gap >= 0f) {
            "Gap must be finite and non-negative: $gap"
        }
    }
}

/** A cascaded [UiStyle] after all unspecified properties have received their initial values. */
internal data class ResolvedUiStyle(
    val color: Int?,
    val backgroundColor: Int?,
    val backgroundMaterial: Mine2DMaterial?,
    val margin: UiEdges,
    val padding: UiEdges,
    val direction: UiDirection,
    val horizontalAlignment: UiHorizontalAlignment,
    val verticalAlignment: UiVerticalAlignment,
    val width: UiLength?,
    val height: UiLength?,
    val position: UiPosition,
    val left: Float?,
    val top: Float?,
    val right: Float?,
    val bottom: Float?,
    val font: Mine2DFont?,
    val gap: Float,
    val boxSizing: UiBoxSizing,
    val noneDisplay: () -> Boolean,
    val boxShadow: UiBoxShadow?,
    val textShadow: UiTextShadow?,
    val dropShadow: UiDropShadow?,
)

internal fun UiStyle.resolveDefaults(): ResolvedUiStyle = ResolvedUiStyle(
    color = color,
    backgroundColor = backgroundColor,
    backgroundMaterial = backgroundMaterial,
    margin = margin ?: UiEdges(),
    padding = padding ?: UiEdges(),
    direction = direction ?: UiDirection.VERTICAL,
    horizontalAlignment = horizontalAlignment ?: UiHorizontalAlignment.LEFT,
    verticalAlignment = verticalAlignment ?: UiVerticalAlignment.TOP,
    width = width,
    height = height,
    position = position ?: UiPosition.STATIC,
    left = left,
    top = top,
    right = right,
    bottom = bottom,
    font = font,
    gap = gap ?: 0f,
    boxSizing = boxSizing ?: UiBoxSizing.CONTENT_BOX,
    noneDisplay = noneDisplay ?: DEFAULT_NONE_DISPLAY,
    boxShadow = boxShadow,
    textShadow = textShadow,
    dropShadow = dropShadow,
)

/** Text properties after resolving inheritance from ancestor styles. */
internal data class ResolvedUiTextStyle(
    val color: Int = UiStyle.DEFAULT_COLOR,
    val font: Mine2DFont? = null,
    val textShadow: UiTextShadow? = null,
)

internal fun ResolvedUiStyle.resolveTextStyle(parent: ResolvedUiTextStyle): ResolvedUiTextStyle =
    ResolvedUiTextStyle(
        color = color ?: parent.color,
        font = font ?: parent.font,
        textShadow = textShadow ?: parent.textShadow,
    )

/**
 * Applies every specified value in [overrides] to this style.
 *
 * Null uniformly means unspecified. Explicit initial values, including zero-valued lengths and
 * the first enum constant, therefore override values supplied by a lower-priority declaration.
 */
internal fun UiStyle.withOverrides(overrides: UiStyle): UiStyle = copy(
    color = overrides.color ?: color,
    backgroundColor = overrides.backgroundColor ?: backgroundColor,
    backgroundMaterial = overrides.backgroundMaterial ?: backgroundMaterial,
    margin = overrides.margin ?: margin,
    padding = overrides.padding ?: padding,
    direction = overrides.direction ?: direction,
    horizontalAlignment = overrides.horizontalAlignment ?: horizontalAlignment,
    verticalAlignment = overrides.verticalAlignment ?: verticalAlignment,
    width = overrides.width ?: width,
    height = overrides.height ?: height,
    position = overrides.position ?: position,
    left = overrides.left ?: left,
    top = overrides.top ?: top,
    right = overrides.right ?: right,
    bottom = overrides.bottom ?: bottom,
    font = overrides.font ?: font,
    gap = overrides.gap ?: gap,
    boxSizing = overrides.boxSizing ?: boxSizing,
    noneDisplay = overrides.noneDisplay ?: noneDisplay,
    boxShadow = overrides.boxShadow ?: boxShadow,
    textShadow = overrides.textShadow ?: textShadow,
    dropShadow = overrides.dropShadow ?: dropShadow,
)
