package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont
import io.github.aiwao.mine2dengine.Mine2DMaterial

/** Physical horizontal alignment used by low-level geometry helpers. */
enum class UiHorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/** Physical vertical alignment used by low-level geometry helpers. */
enum class UiVerticalAlignment {
    TOP,
    CENTER,
    BOTTOM,
}

/** Determines which CSS box an explicit size describes. */
enum class UiBoxSizing {
    CONTENT_BOX,
    BORDER_BOX,
}

/** Supported CSS positioning schemes. */
enum class UiPosition {
    STATIC,
    RELATIVE,
    ABSOLUTE,
}

/**
 * Specified declarations for an element or generated pseudo-element.
 *
 * `null` always means “not declared”, never CSS `auto`. CSS keywords such as `auto`, `none`, and
 * `content` have explicit values in [UiSizeValue], [UiMarginValue], [UiInsetValue], and
 * [UiFlexBasis]. This distinction allows a later declaration to reset a property to its initial
 * CSS value during cascade.
 */
data class UiStyle(
    val color: Int? = null,
    val backgroundColor: Int? = null,
    val backgroundMaterial: Mine2DMaterial? = null,
    val margin: UiMarginDeclaration? = null,
    val padding: UiPaddingDeclaration? = null,
    val display: UiDisplay? = null,
    val width: UiSizeValue? = null,
    val height: UiSizeValue? = null,
    val minWidth: UiSizeValue? = null,
    val minHeight: UiSizeValue? = null,
    val maxWidth: UiSizeValue? = null,
    val maxHeight: UiSizeValue? = null,
    val boxSizing: UiBoxSizing? = null,
    val overflow: UiOverflow? = null,
    val overflowX: UiOverflowValue? = null,
    val overflowY: UiOverflowValue? = null,
    val position: UiPosition? = null,
    val left: UiInsetValue? = null,
    val top: UiInsetValue? = null,
    val right: UiInsetValue? = null,
    val bottom: UiInsetValue? = null,
    val flexDirection: UiFlexDirection? = null,
    val flexWrap: UiFlexWrap? = null,
    val flexGrow: Float? = null,
    val flexShrink: Float? = null,
    val flexBasis: UiFlexBasis? = null,
    val order: Int? = null,
    val justifyContent: UiJustifyContent? = null,
    val alignItems: UiAlignItems? = null,
    val alignSelf: UiAlignSelf? = null,
    val alignContent: UiAlignContent? = null,
    val gap: UiLength? = null,
    val rowGap: UiLength? = null,
    val columnGap: UiLength? = null,
    val textAlign: UiTextAlign? = null,
    val whiteSpace: UiWhiteSpace? = null,
    val font: Mine2DFont? = null,
    val boxShadow: UiBoxShadow? = null,
    val textShadow: UiTextShadow? = null,
    val dropShadow: UiDropShadow? = null,
    val borderRadius: UiBorderRadii? = null,
    val border: UiBorders? = null,
) {
    companion object {
        const val DEFAULT_COLOR: Int = -1
    }

    init {
        validateSize("width", width, allowAuto = true, allowNone = false)
        validateSize("height", height, allowAuto = true, allowNone = false)
        validateSize("minWidth", minWidth, allowAuto = true, allowNone = false)
        validateSize("minHeight", minHeight, allowAuto = true, allowNone = false)
        validateSize("maxWidth", maxWidth, allowAuto = false, allowNone = true)
        validateSize("maxHeight", maxHeight, allowAuto = false, allowNone = true)
        require(flexGrow == null || flexGrow.isFinite() && flexGrow >= 0f) {
            "flexGrow must be finite and non-negative: $flexGrow"
        }
        require(flexShrink == null || flexShrink.isFinite() && flexShrink >= 0f) {
            "flexShrink must be finite and non-negative: $flexShrink"
        }
        listOfNotNull(gap, rowGap, columnGap).forEach { value ->
            require(value.value >= 0f) { "Gap values must be non-negative: $value" }
        }
        validatePadding(padding)
    }
}

private fun validateSize(
    name: String,
    size: UiSizeValue?,
    allowAuto: Boolean,
    allowNone: Boolean,
) {
    require(allowAuto || size !is UiSizeValue.Auto) { "$name does not accept auto" }
    require(allowNone || size !is UiSizeValue.None) { "$name does not accept none" }
    require(size !is UiLength || size.value >= 0f) { "$name must be non-negative: $size" }
}

private fun validatePadding(padding: UiPaddingDeclaration?) {
    if (padding is UiPaddings) {
        listOf(padding.top, padding.right, padding.bottom, padding.left).forEach { value ->
            require(value.value >= 0f) { "Padding values must be non-negative: $value" }
        }
    }
}

/** Cascaded declarations after CSS initial values have been applied. */
internal data class ResolvedUiStyle(
    val color: Int?,
    val backgroundColor: Int?,
    val backgroundMaterial: Mine2DMaterial?,
    val borderRadius: UiBorderRadii,
    val margin: UiMargins,
    val padding: UiPaddings,
    val border: UiBorders,
    val display: UiDisplay,
    val width: UiSizeValue,
    val height: UiSizeValue,
    val minWidth: UiSizeValue,
    val minHeight: UiSizeValue,
    val maxWidth: UiSizeValue,
    val maxHeight: UiSizeValue,
    val boxSizing: UiBoxSizing,
    val overflow: ResolvedUiOverflow,
    val position: UiPosition,
    val left: UiInsetValue,
    val top: UiInsetValue,
    val right: UiInsetValue,
    val bottom: UiInsetValue,
    val flexDirection: UiFlexDirection,
    val flexWrap: UiFlexWrap,
    val flexGrow: Float,
    val flexShrink: Float,
    val flexBasis: UiFlexBasis,
    val order: Int,
    val justifyContent: UiJustifyContent,
    val alignItems: UiAlignItems,
    val alignSelf: UiAlignSelf,
    val alignContent: UiAlignContent,
    val rowGap: UiLength,
    val columnGap: UiLength,
    val textAlign: UiTextAlign?,
    val whiteSpace: UiWhiteSpace?,
    val font: Mine2DFont?,
    val boxShadow: UiBoxShadow?,
    val textShadow: UiTextShadow?,
    val dropShadow: UiDropShadow?,
)

internal fun UiStyle.resolveDefaults(
    initialDisplay: UiDisplay = UiDisplay.INLINE,
): ResolvedUiStyle {
    val commonGap = gap ?: 0f.px
    val resolvedOverflow = resolveOverflow(
        x = overflowX ?: overflow?.x ?: UiOverflowValue.VISIBLE,
        y = overflowY ?: overflow?.y ?: UiOverflowValue.VISIBLE,
    )
    return ResolvedUiStyle(
        color = color,
        backgroundColor = backgroundColor,
        backgroundMaterial = backgroundMaterial,
        borderRadius = borderRadius ?: UiBorderRadii.ZERO,
        margin = margin.toMargins(),
        padding = padding.toPaddings(),
        border = border ?: UiBorders.NONE,
        display = display ?: initialDisplay,
        width = width ?: UiSizeValue.AUTO,
        height = height ?: UiSizeValue.AUTO,
        minWidth = minWidth ?: UiSizeValue.AUTO,
        minHeight = minHeight ?: UiSizeValue.AUTO,
        maxWidth = maxWidth ?: UiSizeValue.NONE,
        maxHeight = maxHeight ?: UiSizeValue.NONE,
        boxSizing = boxSizing ?: UiBoxSizing.CONTENT_BOX,
        overflow = resolvedOverflow,
        position = position ?: UiPosition.STATIC,
        left = left ?: UiInsetValue.AUTO,
        top = top ?: UiInsetValue.AUTO,
        right = right ?: UiInsetValue.AUTO,
        bottom = bottom ?: UiInsetValue.AUTO,
        flexDirection = flexDirection ?: UiFlexDirection.ROW,
        flexWrap = flexWrap ?: UiFlexWrap.NOWRAP,
        flexGrow = flexGrow ?: 0f,
        flexShrink = flexShrink ?: 1f,
        flexBasis = flexBasis ?: UiFlexBasis.AUTO,
        order = order ?: 0,
        justifyContent = justifyContent ?: UiJustifyContent.NORMAL,
        alignItems = alignItems ?: UiAlignItems.NORMAL,
        alignSelf = alignSelf ?: UiAlignSelf.AUTO,
        alignContent = alignContent ?: UiAlignContent.NORMAL,
        rowGap = rowGap ?: commonGap,
        columnGap = columnGap ?: commonGap,
        textAlign = textAlign,
        whiteSpace = whiteSpace,
        font = font,
        boxShadow = boxShadow,
        textShadow = textShadow,
        dropShadow = dropShadow,
    )
}

private fun UiMarginDeclaration?.toMargins(): UiMargins = when (this) {
    null -> UiMargins()
    is UiMargins -> this
}

private fun UiPaddingDeclaration?.toPaddings(): UiPaddings = when (this) {
    null -> UiPaddings()
    is UiPaddings -> this
}

/** Inherited text properties after cascade. */
internal data class ResolvedUiTextStyle(
    val color: Int = UiStyle.DEFAULT_COLOR,
    val font: Mine2DFont? = null,
    val textShadow: UiTextShadow? = null,
    val textAlign: UiTextAlign = UiTextAlign.START,
    val whiteSpace: UiWhiteSpace = UiWhiteSpace.NORMAL,
)

internal fun ResolvedUiStyle.resolveTextStyle(parent: ResolvedUiTextStyle): ResolvedUiTextStyle =
    ResolvedUiTextStyle(
        color = color ?: parent.color,
        font = font ?: parent.font,
        textShadow = textShadow ?: parent.textShadow,
        textAlign = textAlign ?: parent.textAlign,
        whiteSpace = whiteSpace ?: parent.whiteSpace,
    )

/** Applies each declared value in [overrides], preserving omitted declarations. */
internal fun UiStyle.withOverrides(overrides: UiStyle): UiStyle = copy(
    color = overrides.color ?: color,
    backgroundColor = overrides.backgroundColor ?: backgroundColor,
    backgroundMaterial = overrides.backgroundMaterial ?: backgroundMaterial,
    borderRadius = overrides.borderRadius ?: borderRadius,
    margin = overrides.margin ?: margin,
    padding = overrides.padding ?: padding,
    border = overrides.border ?: border,
    display = overrides.display ?: display,
    width = overrides.width ?: width,
    height = overrides.height ?: height,
    minWidth = overrides.minWidth ?: minWidth,
    minHeight = overrides.minHeight ?: minHeight,
    maxWidth = overrides.maxWidth ?: maxWidth,
    maxHeight = overrides.maxHeight ?: maxHeight,
    boxSizing = overrides.boxSizing ?: boxSizing,
    overflow = overrides.overflow ?: overflow,
    // A later shorthand resets an earlier longhand. When both occur in the same UiStyle value,
    // the explicit longhand wins because Kotlin constructor arguments have no declaration order.
    overflowX = overrides.overflowX ?: if (overrides.overflow != null) null else overflowX,
    overflowY = overrides.overflowY ?: if (overrides.overflow != null) null else overflowY,
    position = overrides.position ?: position,
    left = overrides.left ?: left,
    top = overrides.top ?: top,
    right = overrides.right ?: right,
    bottom = overrides.bottom ?: bottom,
    flexDirection = overrides.flexDirection ?: flexDirection,
    flexWrap = overrides.flexWrap ?: flexWrap,
    flexGrow = overrides.flexGrow ?: flexGrow,
    flexShrink = overrides.flexShrink ?: flexShrink,
    flexBasis = overrides.flexBasis ?: flexBasis,
    order = overrides.order ?: order,
    justifyContent = overrides.justifyContent ?: justifyContent,
    alignItems = overrides.alignItems ?: alignItems,
    alignSelf = overrides.alignSelf ?: alignSelf,
    alignContent = overrides.alignContent ?: alignContent,
    gap = overrides.gap ?: gap,
    rowGap = overrides.rowGap ?: rowGap,
    columnGap = overrides.columnGap ?: columnGap,
    textAlign = overrides.textAlign ?: textAlign,
    whiteSpace = overrides.whiteSpace ?: whiteSpace,
    font = overrides.font ?: font,
    boxShadow = overrides.boxShadow ?: boxShadow,
    textShadow = overrides.textShadow ?: textShadow,
    dropShadow = overrides.dropShadow ?: dropShadow,
)

/** Computed physical overflow values after the two axes have interacted. */
data class ResolvedUiOverflow(
    val x: UiOverflowValue,
    val y: UiOverflowValue,
)

private fun resolveOverflow(
    x: UiOverflowValue,
    y: UiOverflowValue,
): ResolvedUiOverflow = ResolvedUiOverflow(
    x = if (x == UiOverflowValue.VISIBLE && y.isScrollable) UiOverflowValue.AUTO else x,
    y = if (y == UiOverflowValue.VISIBLE && x.isScrollable) UiOverflowValue.AUTO else y,
)

/** Lowest-priority UA declaration for the supported HTML-like element tags. */
internal fun userAgentStyleFor(element: UiElement): UiStyle {
    if (element is InputControl) {
        return UiStyle(
            display = UiDisplay.Box(UiDisplayOutside.INLINE, UiDisplayInside.FLOW_ROOT),
        )
    }
    val blockTags = setOf(
        "address", "article", "aside", "blockquote", "div", "footer", "form", "h1", "h2",
        "h3", "h4", "h5", "h6", "header", "hr", "main", "nav", "ol", "p", "pre",
        "section", "ul",
    )
    return UiStyle(display = if (element.tag.lowercase() in blockTags) UiDisplay.BLOCK else UiDisplay.INLINE)
}
