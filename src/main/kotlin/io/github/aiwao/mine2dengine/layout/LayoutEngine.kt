package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont

/** Supplies the text measurements required by the layout pass. */
interface UiTextMeasurer {
    val lineHeight: Float

    fun width(text: String): Float
}

/** Text measurements backed by a loaded [Mine2DFont]. */
class Mine2DTextMeasurer(
    private val font: Mine2DFont,
) : UiTextMeasurer {
    override val lineHeight: Float
        get() = font.lineHeight

    override fun width(text: String): Float = font.width(text)
}

/**
 * Measures and positions a tree of [Div] and [Paragraph] nodes.
 *
 * [left] and [top] passed to [layout] are the top-left coordinate of the root's outer box.
 * Text fonts are selected through [UiStyle.font] and remain owned by the caller.
 */
object LayoutEngine {
    /** Builds a root [Div] with [rootStyle], then calculates the complete UI tree. */
    @JvmStatic
    fun layout(
        rootStyle: UiStyle = UiStyle(),
        left: Float = 0f,
        top: Float = 0f,
        content: Div.() -> Unit,
    ): UiLayout = layout(div(style = rootStyle, content = content), left, top)

    /** Builds a root [Div], applies [styleSheet], then calculates the complete UI tree. */
    @JvmStatic
    fun layout(
        styleSheet: StyleSheet,
        rootStyle: UiStyle = UiStyle(),
        left: Float = 0f,
        top: Float = 0f,
        content: Div.() -> Unit,
    ): UiLayout = layout(
        div(style = rootStyle, content = content),
        styleSheet,
        left,
        top,
    )

    /** Builds a root [Div], cascades [styleSheets], then calculates the complete UI tree. */
    @JvmStatic
    fun layout(
        styleSheets: Iterable<StyleSheet>,
        rootStyle: UiStyle = UiStyle(),
        left: Float = 0f,
        top: Float = 0f,
        content: Div.() -> Unit,
    ): UiLayout = layout(
        div(style = rootStyle, content = content),
        styleSheets,
        left,
        top,
    )

    /** Calculates the complete UI tree without style sheets or draw calls. */
    @JvmStatic
    fun layout(root: UiElement, left: Float = 0f, top: Float = 0f): UiLayout =
        layout(root, emptyList(), left, top)

    /** Calculates the complete UI tree after applying [styleSheet]. */
    @JvmStatic
    fun layout(
        root: UiElement,
        styleSheet: StyleSheet,
        left: Float = 0f,
        top: Float = 0f,
    ): UiLayout = layout(root, listOf(styleSheet), left, top)

    /**
     * Calculates the complete UI tree after cascading [styleSheets] in iteration order.
     *
     * A later sheet acts as though its rules were appended after all rules in earlier sheets.
     */
    @JvmStatic
    fun layout(
        root: UiElement,
        styleSheets: Iterable<StyleSheet>,
        left: Float = 0f,
        top: Float = 0f,
    ): UiLayout = calculateLayout(root, left, top, styleSheets) { element, font ->
        Mine2DTextMeasurer(
            requireNotNull(font) {
                "${element.javaClass.simpleName} requires a font in its style or an ancestor style"
            },
        )
    }
}

internal fun calculateLayout(
    root: UiElement,
    left: Float,
    top: Float,
    textMeasurer: UiTextMeasurer,
    styleSheets: Iterable<StyleSheet> = emptyList(),
): UiLayout {
    validateTextMeasurer(textMeasurer)
    return calculateLayout(root, left, top, styleSheets) { _, _ -> textMeasurer }
}

private fun calculateLayout(
    root: UiElement,
    left: Float,
    top: Float,
    styleSheets: Iterable<StyleSheet>,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
): UiLayout {
    require(left.isFinite()) { "Left must be finite: $left" }
    require(top.isFinite()) { "Top must be finite: $top" }
    val sheets = styleSheets.toList()

    fun calculateSnapshot(
        snapshotLeft: Float,
        snapshotTop: Float,
        evaluatedNoneDisplays: Map<UiElement, Boolean>,
    ): UiLayoutSnapshot {
        val noneDisplayStates = mutableListOf<UiNoneDisplayState>()
        val measured = measure(
            element = root,
            styleSheetContext = StyleSheetElementContext(root),
            parentContentWidth = null,
            parentContentHeight = null,
            absoluteContainingWidth = null,
            absoluteContainingHeight = null,
            inheritedTextStyle = ResolvedUiTextStyle(),
            inheritedDescendantStyle = { _ -> null },
            parentChildStyle = { null },
            styleSheetStyle = { context -> sheets.styleFor(context) },
            textMeasurer = textMeasurer,
            noneDisplayStates = noneDisplayStates,
            evaluatedNoneDisplays = evaluatedNoneDisplays,
        )
        return UiLayoutSnapshot(
            root = place(measured, snapshotLeft, snapshotTop),
            noneDisplayStates = noneDisplayStates,
        )
    }

    val snapshot = calculateSnapshot(left, top, emptyMap())
    return UiLayout(snapshot, ::calculateSnapshot)
}

private data class MeasuredNode(
    val element: UiElement,
    val style: ResolvedUiStyle,
    val styleProvider: () -> ResolvedUiStyle,
    val contentSize: UiSize,
    val children: List<MeasuredNode>,
    val textStyle: ResolvedUiTextStyle,
    val displayed: Boolean,
) {
    val boundsSize: UiSize = if (displayed) {
        UiSize(
            width = contentSize.width + style.padding.horizontal,
            height = contentSize.height + style.padding.vertical,
        )
    } else {
        UiSize(0f, 0f)
    }

    val outerSize: UiSize = if (displayed) {
        UiSize(
            width = boundsSize.width + style.margin.horizontal,
            height = boundsSize.height + style.margin.vertical,
        )
    } else {
        UiSize(0f, 0f)
    }
}

private fun measure(
    element: UiElement,
    styleSheetContext: StyleSheetElementContext,
    parentContentWidth: Float?,
    parentContentHeight: Float?,
    absoluteContainingWidth: Float?,
    absoluteContainingHeight: Float?,
    inheritedTextStyle: ResolvedUiTextStyle,
    inheritedDescendantStyle: (UiElement) -> UiStyle?,
    parentChildStyle: () -> UiStyle?,
    styleSheetStyle: (StyleSheetElementContext) -> UiStyle?,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
    noneDisplayStates: MutableList<UiNoneDisplayState>,
    evaluatedNoneDisplays: Map<UiElement, Boolean>,
): MeasuredNode {
    val styleProvider = {
        combineStyles(
            styleSheetStyle(styleSheetContext),
            combineStyles(
                inheritedDescendantStyle(element),
                parentChildStyle(),
            ),
        )?.withOverrides(element.style)
            ?.resolveDefaults()
            ?: element.style.resolveDefaults()
    }
    val style = styleProvider()
    val percentageWidthBase = when (style.position) {
        UiPosition.STATIC, UiPosition.RELATIVE -> parentContentWidth
        UiPosition.ABSOLUTE -> absoluteContainingWidth
    }
    val percentageHeightBase = when (style.position) {
        UiPosition.STATIC, UiPosition.RELATIVE -> parentContentHeight
        UiPosition.ABSOLUTE -> absoluteContainingHeight
    }
    val resolvedWidth = style.width?.resolve(percentageWidthBase)
    val resolvedHeight = style.height?.resolve(percentageHeightBase)
    val definiteContentWidth = resolvedWidth?.let {
        contentLength(
            specifiedLength = it,
            naturalLength = 0f,
            padding = style.padding.horizontal,
            boxSizing = style.boxSizing,
        )
    } ?: if (
        style.position == UiPosition.ABSOLUTE &&
        absoluteContainingWidth != null &&
        style.left != null &&
        style.right != null
    ) {
        (absoluteContainingWidth - style.left - style.right - style.margin.horizontal -
            style.padding.horizontal).coerceAtLeast(0f)
    } else {
        null
    }
    val definiteContentHeight = resolvedHeight?.let {
        contentLength(
            specifiedLength = it,
            naturalLength = 0f,
            padding = style.padding.vertical,
            boxSizing = style.boxSizing,
        )
    } ?: if (
        style.position == UiPosition.ABSOLUTE &&
        absoluteContainingHeight != null &&
        style.top != null &&
        style.bottom != null
    ) {
        (absoluteContainingHeight - style.top - style.bottom - style.margin.vertical -
            style.padding.vertical).coerceAtLeast(0f)
    } else {
        null
    }
    val boundsWidth = definiteContentWidth?.let { it + style.padding.horizontal }
    val boundsHeight = definiteContentHeight?.let { it + style.padding.vertical }
    val descendantAbsoluteContainingWidth = when {
        absoluteContainingWidth == null -> boundsWidth
        style.position != UiPosition.STATIC -> boundsWidth
        else -> absoluteContainingWidth
    }
    val descendantAbsoluteContainingHeight = when {
        absoluteContainingHeight == null -> boundsHeight
        style.position != UiPosition.STATIC -> boundsHeight
        else -> absoluteContainingHeight
    }
    val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
    val noneDisplay = evaluatedNoneDisplays[element] ?: style.noneDisplay()
    noneDisplayStates += UiNoneDisplayState(
        element = element,
        predicate = style.noneDisplay,
        noneDisplay = noneDisplay,
    )

    if (noneDisplay) {
        return MeasuredNode(
            element = element,
            style = style,
            styleProvider = styleProvider,
            contentSize = UiSize(0f, 0f),
            children = emptyList(),
            textStyle = resolvedTextStyle,
            displayed = false,
        )
    }

    val children = if (element is UiContainer) {
        val descendantStyle = { descendant: UiElement ->
            combineStyles(
                inheritedDescendantStyle(descendant),
                element.descendantStyle?.invoke(descendant),
            )
        }
        var previousSiblingContext: StyleSheetElementContext? = null
        element.children
            .map { child ->
                val childStyleSheetContext = StyleSheetElementContext(
                    element = child,
                    parent = styleSheetContext,
                    previousSibling = previousSiblingContext,
                )
                previousSiblingContext = childStyleSheetContext
                measure(
                    element = child,
                    styleSheetContext = childStyleSheetContext,
                    parentContentWidth = definiteContentWidth,
                    parentContentHeight = definiteContentHeight,
                    absoluteContainingWidth = descendantAbsoluteContainingWidth,
                    absoluteContainingHeight = descendantAbsoluteContainingHeight,
                    inheritedTextStyle = resolvedTextStyle,
                    inheritedDescendantStyle = descendantStyle,
                    parentChildStyle = { element.childStyle?.invoke(child) },
                    styleSheetStyle = styleSheetStyle,
                    textMeasurer = textMeasurer,
                    noneDisplayStates = noneDisplayStates,
                    evaluatedNoneDisplays = evaluatedNoneDisplays,
                )
            }
            .filter(MeasuredNode::displayed)
    } else {
        emptyList()
    }

    val textSize = when (element) {
        is Paragraph -> measureText(element.text, textMeasurer(element, resolvedTextStyle.font))
        is UiContainer -> null
    }

    val naturalSize = when (element) {
        is UiContainer -> measureChildren(children, style.direction, style.gap)
        is Paragraph -> checkNotNull(textSize)
    }

    return MeasuredNode(
        element = element,
        style = style,
        styleProvider = styleProvider,
        contentSize = UiSize(
            width = contentLength(
                specifiedLength = resolvedWidth,
                naturalLength = naturalSize.width,
                padding = style.padding.horizontal,
                boxSizing = style.boxSizing,
            ),
            height = contentLength(
                specifiedLength = resolvedHeight,
                naturalLength = naturalSize.height,
                padding = style.padding.vertical,
                boxSizing = style.boxSizing,
            ),
        ),
        children = children,
        textStyle = resolvedTextStyle,
        displayed = true,
    )
}

private fun combineStyles(base: UiStyle?, overrides: UiStyle?): UiStyle? = when {
    base == null -> overrides
    overrides == null -> base
    else -> base.withOverrides(overrides)
}

private fun contentLength(
    specifiedLength: Float?,
    naturalLength: Float,
    padding: Float,
    boxSizing: UiBoxSizing,
): Float {
    val length = specifiedLength ?: return naturalLength
    return when (boxSizing) {
        UiBoxSizing.CONTENT_BOX -> length
        UiBoxSizing.BORDER_BOX -> (length - padding).coerceAtLeast(0f)
    }
}

private fun measureText(text: String, textMeasurer: UiTextMeasurer): UiSize {
    validateTextMeasurer(textMeasurer)
    val lines = textLines(text)
    val widths = lines.map(textMeasurer::width)
    require(widths.all { it.isFinite() && it >= 0f }) {
        "Text widths must be finite and non-negative"
    }
    return UiSize(
        width = widths.maxOrNull() ?: 0f,
        height = lines.size * textMeasurer.lineHeight,
    )
}

private fun validateTextMeasurer(textMeasurer: UiTextMeasurer) {
    require(textMeasurer.lineHeight.isFinite() && textMeasurer.lineHeight >= 0f) {
        "Text line height must be finite and non-negative: ${textMeasurer.lineHeight}"
    }
}

private fun measureChildren(
    children: List<MeasuredNode>,
    direction: UiDirection,
    gap: Float,
): UiSize {
    val normalChildren = children.filter { it.style.position != UiPosition.ABSOLUTE }
    val totalGap = gap * (normalChildren.size - 1).coerceAtLeast(0)
    return when (direction) {
        UiDirection.VERTICAL -> UiSize(
            width = normalChildren.maxOfOrNull { it.outerSize.width } ?: 0f,
            height = normalChildren.sumOf { it.outerSize.height.toDouble() }.toFloat() + totalGap,
        )

        UiDirection.HORIZONTAL -> UiSize(
            width = normalChildren.sumOf { it.outerSize.width.toDouble() }.toFloat() + totalGap,
            height = normalChildren.maxOfOrNull { it.outerSize.height } ?: 0f,
        )
    }
}

private fun place(
    measured: MeasuredNode,
    staticOuterLeft: Float,
    staticOuterTop: Float,
    absoluteContainingBlock: UiRect? = null,
): UiLayoutNode {
    val style = measured.style
    if (!measured.displayed) {
        val emptyBounds = UiRect(staticOuterLeft, staticOuterTop, 0f, 0f)
        return UiLayoutNode(
            element = measured.element,
            outerBounds = emptyBounds,
            bounds = emptyBounds,
            contentBounds = emptyBounds,
            children = emptyList(),
            font = measured.textStyle.font,
            color = measured.textStyle.color,
            textShadow = measured.textStyle.textShadow,
            displayed = false,
        )
    }

    val positioned = if (style.position == UiPosition.ABSOLUTE) {
        measured.stretchedTo(absoluteContainingBlock)
    } else {
        measured
    }
    val outerLeft = positionedOuterLeft(
        measured = positioned,
        staticOuterLeft = staticOuterLeft,
        containingBlock = absoluteContainingBlock,
    )
    val outerTop = positionedOuterTop(
        measured = positioned,
        staticOuterTop = staticOuterTop,
        containingBlock = absoluteContainingBlock,
    )

    val bounds = UiRect(
        left = outerLeft + style.margin.left,
        top = outerTop + style.margin.top,
        width = positioned.boundsSize.width,
        height = positioned.boundsSize.height,
    )
    val contentBounds = UiRect(
        left = bounds.left + style.padding.left,
        top = bounds.top + style.padding.top,
        width = positioned.contentSize.width,
        height = positioned.contentSize.height,
    )

    val descendantContainingBlock = when {
        absoluteContainingBlock == null -> bounds
        style.position != UiPosition.STATIC -> bounds
        else -> absoluteContainingBlock
    }
    val children = placeChildren(positioned, contentBounds, descendantContainingBlock)
    return UiLayoutNode(
        element = positioned.element,
        outerBounds = UiRect(
            outerLeft,
            outerTop,
            positioned.outerSize.width,
            positioned.outerSize.height,
        ),
        bounds = bounds,
        contentBounds = contentBounds,
        children = children,
        font = positioned.textStyle.font,
        color = positioned.textStyle.color,
        textShadow = positioned.textStyle.textShadow,
        displayed = positioned.displayed,
    ).also { node ->
        node.styleProvider = positioned.styleProvider
    }
}

private fun placeChildren(
    measured: MeasuredNode,
    contentBounds: UiRect,
    absoluteContainingBlock: UiRect,
): List<UiLayoutNode> {
    if (measured.children.isEmpty()) return emptyList()

    val style = measured.style
    val normalChildren = measured.children.filter { it.style.position != UiPosition.ABSOLUTE }
    return when (style.direction) {
        UiDirection.VERTICAL -> {
            val childrenHeight = normalChildren
                .sumOf { it.outerSize.height.toDouble() }
                .toFloat() + style.gap * (normalChildren.size - 1).coerceAtLeast(0)
            var top = contentBounds.top + alignedTop(
                availableHeight = contentBounds.height,
                itemHeight = childrenHeight,
                alignment = style.verticalAlignment,
            )
            var placedNormalChildren = 0
            measured.children.map { child ->
                val left = contentBounds.left + alignedLeft(
                    availableWidth = contentBounds.width,
                    itemWidth = child.outerSize.width,
                    alignment = style.horizontalAlignment,
                )
                place(child, left, top, absoluteContainingBlock).also {
                    if (child.style.position != UiPosition.ABSOLUTE) {
                        placedNormalChildren++
                        top += child.outerSize.height
                        if (placedNormalChildren < normalChildren.size) top += style.gap
                    }
                }
            }
        }

        UiDirection.HORIZONTAL -> {
            val childrenWidth = normalChildren
                .sumOf { it.outerSize.width.toDouble() }
                .toFloat() + style.gap * (normalChildren.size - 1).coerceAtLeast(0)
            var left = contentBounds.left + alignedLeft(
                availableWidth = contentBounds.width,
                itemWidth = childrenWidth,
                alignment = style.horizontalAlignment,
            )
            var placedNormalChildren = 0
            measured.children.map { child ->
                val top = contentBounds.top + alignedTop(
                    availableHeight = contentBounds.height,
                    itemHeight = child.outerSize.height,
                    alignment = style.verticalAlignment,
                )
                place(child, left, top, absoluteContainingBlock).also {
                    if (child.style.position != UiPosition.ABSOLUTE) {
                        placedNormalChildren++
                        left += child.outerSize.width
                        if (placedNormalChildren < normalChildren.size) left += style.gap
                    }
                }
            }
        }
    }
}

private fun MeasuredNode.stretchedTo(containingBlock: UiRect?): MeasuredNode {
    containingBlock ?: return this

    val stretchedWidth = if (style.width == null && style.left != null && style.right != null) {
        (containingBlock.width - style.left - style.right - style.margin.horizontal -
            style.padding.horizontal).coerceAtLeast(0f)
    } else {
        contentSize.width
    }
    val stretchedHeight = if (style.height == null && style.top != null && style.bottom != null) {
        (containingBlock.height - style.top - style.bottom - style.margin.vertical -
            style.padding.vertical).coerceAtLeast(0f)
    } else {
        contentSize.height
    }
    if (stretchedWidth == contentSize.width && stretchedHeight == contentSize.height) return this

    return copy(contentSize = UiSize(stretchedWidth, stretchedHeight))
}

private fun positionedOuterLeft(
    measured: MeasuredNode,
    staticOuterLeft: Float,
    containingBlock: UiRect?,
): Float = when (measured.style.position) {
    UiPosition.STATIC -> staticOuterLeft
    UiPosition.RELATIVE -> if (containingBlock == null) {
        staticOuterLeft
    } else {
        staticOuterLeft + when {
            measured.style.left != null -> measured.style.left
            measured.style.right != null -> -measured.style.right
            else -> 0f
        }
    }

    UiPosition.ABSOLUTE -> when {
        containingBlock == null -> staticOuterLeft
        measured.style.left != null -> containingBlock.left + measured.style.left
        measured.style.right != null ->
            containingBlock.right - measured.style.right - measured.outerSize.width
        else -> staticOuterLeft
    }
}

private fun positionedOuterTop(
    measured: MeasuredNode,
    staticOuterTop: Float,
    containingBlock: UiRect?,
): Float = when (measured.style.position) {
    UiPosition.STATIC -> staticOuterTop
    UiPosition.RELATIVE -> if (containingBlock == null) {
        staticOuterTop
    } else {
        staticOuterTop + when {
            measured.style.top != null -> measured.style.top
            measured.style.bottom != null -> -measured.style.bottom
            else -> 0f
        }
    }

    UiPosition.ABSOLUTE -> when {
        containingBlock == null -> staticOuterTop
        measured.style.top != null -> containingBlock.top + measured.style.top
        measured.style.bottom != null ->
            containingBlock.bottom - measured.style.bottom - measured.outerSize.height
        else -> staticOuterTop
    }
}

internal fun alignedLeft(
    availableWidth: Float,
    itemWidth: Float,
    alignment: UiHorizontalAlignment,
): Float = when (alignment) {
    UiHorizontalAlignment.LEFT -> 0f
    UiHorizontalAlignment.CENTER -> (availableWidth - itemWidth) / 2f
    UiHorizontalAlignment.RIGHT -> availableWidth - itemWidth
}

internal fun alignedTop(
    availableHeight: Float,
    itemHeight: Float,
    alignment: UiVerticalAlignment,
): Float = when (alignment) {
    UiVerticalAlignment.TOP -> 0f
    UiVerticalAlignment.CENTER -> (availableHeight - itemHeight) / 2f
    UiVerticalAlignment.BOTTOM -> availableHeight - itemHeight
}

internal fun textLines(text: String): List<String> =
    text.split("\n", ignoreCase = false, limit = Int.MAX_VALUE)
