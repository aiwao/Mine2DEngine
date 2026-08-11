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
        evaluatedNoneDisplays: Map<UiNoneDisplayKey, Boolean>,
    ): UiLayoutSnapshot {
        val noneDisplayStates = mutableListOf<UiNoneDisplayState>()
        val measured = measure(
            element = root,
            globalStyleSheetScope = StyleSheetScopeContext(
                styleSheets = sheets,
                context = StyleSheetElementContext(root),
            ),
            scopedStyleSheetScopes = emptyList(),
            parentContentWidth = null,
            parentContentHeight = null,
            absoluteContainingWidth = null,
            absoluteContainingHeight = null,
            inheritedTextStyle = ResolvedUiTextStyle(),
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
    val contents: List<MeasuredContent>,
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

private data class MeasuredPseudoNode(
    val element: UiElement,
    val pseudoElement: UiPseudoElement,
    val content: UiGeneratedContent,
    val pseudoStyleProvider: () -> UiPseudoStyle,
    val style: ResolvedUiStyle,
    val contentSize: UiSize,
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

private sealed interface MeasuredContent {
    data class Element(
        val node: MeasuredNode,
    ) : MeasuredContent

    data class PseudoElement(
        val node: MeasuredPseudoNode,
    ) : MeasuredContent

    data class Text(
        val size: UiSize,
    ) : MeasuredContent
}

private val MeasuredContent.outerSize: UiSize
    get() = when (this) {
        is MeasuredContent.Element -> node.outerSize
        is MeasuredContent.PseudoElement -> node.outerSize
        is MeasuredContent.Text -> size
    }

private val MeasuredContent.position: UiPosition
    get() = when (this) {
        is MeasuredContent.Element -> node.style.position
        is MeasuredContent.PseudoElement -> node.style.position
        is MeasuredContent.Text -> UiPosition.STATIC
    }

private fun measure(
    element: UiElement,
    globalStyleSheetScope: StyleSheetScopeContext,
    scopedStyleSheetScopes: List<StyleSheetScopeContext>,
    parentContentWidth: Float?,
    parentContentHeight: Float?,
    absoluteContainingWidth: Float?,
    absoluteContainingHeight: Float?,
    inheritedTextStyle: ResolvedUiTextStyle,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
    noneDisplayStates: MutableList<UiNoneDisplayState>,
    evaluatedNoneDisplays: Map<UiNoneDisplayKey, Boolean>,
): MeasuredNode {
    val ownContainerStyleSheetScope = (element as? UiContainer)
        ?.styleSheets
        ?.takeIf { it.isNotEmpty() }
        ?.let { styleSheets ->
            StyleSheetScopeContext(
                styleSheets = styleSheets.toList(),
                context = StyleSheetElementContext(element),
            )
        }
    val ownComponentStyleSheetScope = element.componentStyleSheets?.let { styleSheets ->
        StyleSheetScopeContext(
            styleSheets = styleSheets,
            context = StyleSheetElementContext(element),
        )
    }
    val ownStyleSheetScopes = listOfNotNull(
        ownContainerStyleSheetScope,
        ownComponentStyleSheetScope,
    )
    val elementScopedStyleSheetScopes = scopedStyleSheetScopes + ownStyleSheetScopes
    val elementStyleSheetScopes = listOf(globalStyleSheetScope) +
        elementScopedStyleSheetScopes
    val styleProvider = {
        elementStyleSheetScopes.scopedStyleFor()
            ?.withOverrides(element.style)
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
    val displayKey = UiNoneDisplayKey(element)
    val noneDisplay = evaluatedNoneDisplays[displayKey] ?: style.noneDisplay()
    noneDisplayStates += UiNoneDisplayState(
        key = displayKey,
        predicate = style.noneDisplay,
        noneDisplay = noneDisplay,
    )

    if (noneDisplay) {
        return MeasuredNode(
            element = element,
            style = style,
            styleProvider = styleProvider,
            contentSize = UiSize(0f, 0f),
            contents = emptyList(),
            textStyle = resolvedTextStyle,
            displayed = false,
        )
    }

    val children = if (element is UiContainer) {
        val scopedStyleSheetScopesForChildren = if (ownComponentStyleSheetScope == null) {
            elementScopedStyleSheetScopes
        } else {
            ownStyleSheetScopes
        }
        var previousGlobalSiblingContext: StyleSheetElementContext? = null
        val previousScopedSiblingContexts = MutableList<StyleSheetElementContext?>(
            scopedStyleSheetScopesForChildren.size,
        ) { null }
        element.children
            .map { child ->
                val childGlobalStyleSheetContext = StyleSheetElementContext(
                    element = child,
                    parent = globalStyleSheetScope.context,
                    previousSibling = previousGlobalSiblingContext,
                )
                previousGlobalSiblingContext = childGlobalStyleSheetContext
                val childScopedStyleSheetScopes = scopedStyleSheetScopesForChildren
                    .mapIndexed { index, scope ->
                        StyleSheetScopeContext(
                            styleSheets = scope.styleSheets,
                            context = StyleSheetElementContext(
                                element = child,
                                parent = scope.context,
                                previousSibling = previousScopedSiblingContexts[index],
                            ).also { context ->
                                previousScopedSiblingContexts[index] = context
                            },
                        )
                    }
                measure(
                    element = child,
                    globalStyleSheetScope = StyleSheetScopeContext(
                        styleSheets = globalStyleSheetScope.styleSheets,
                        context = childGlobalStyleSheetContext,
                    ),
                    scopedStyleSheetScopes = childScopedStyleSheetScopes,
                    parentContentWidth = definiteContentWidth,
                    parentContentHeight = definiteContentHeight,
                    absoluteContainingWidth = descendantAbsoluteContainingWidth,
                    absoluteContainingHeight = descendantAbsoluteContainingHeight,
                    inheritedTextStyle = resolvedTextStyle,
                    textMeasurer = textMeasurer,
                    noneDisplayStates = noneDisplayStates,
                    evaluatedNoneDisplays = evaluatedNoneDisplays,
                )
            }
            .filter(MeasuredNode::displayed)
    } else {
        emptyList()
    }

    val beforePseudo = elementStyleSheetScopes
        .scopedPseudoStyleFor(UiPseudoElement.BEFORE)
        ?.let { pseudoStyleProvider ->
            measurePseudoElement(
                element = element,
                pseudoElement = UiPseudoElement.BEFORE,
                pseudoStyleProvider = pseudoStyleProvider,
                parentContentWidth = definiteContentWidth,
                parentContentHeight = definiteContentHeight,
                absoluteContainingWidth = descendantAbsoluteContainingWidth,
                absoluteContainingHeight = descendantAbsoluteContainingHeight,
                inheritedTextStyle = resolvedTextStyle,
                textMeasurer = textMeasurer,
                noneDisplayStates = noneDisplayStates,
                evaluatedNoneDisplays = evaluatedNoneDisplays,
            )
        }

    val afterPseudo = elementStyleSheetScopes
        .scopedPseudoStyleFor(UiPseudoElement.AFTER)
        ?.let { pseudoStyleProvider ->
            measurePseudoElement(
                element = element,
                pseudoElement = UiPseudoElement.AFTER,
                pseudoStyleProvider = pseudoStyleProvider,
                parentContentWidth = definiteContentWidth,
                parentContentHeight = definiteContentHeight,
                absoluteContainingWidth = descendantAbsoluteContainingWidth,
                absoluteContainingHeight = descendantAbsoluteContainingHeight,
                inheritedTextStyle = resolvedTextStyle,
                textMeasurer = textMeasurer,
                noneDisplayStates = noneDisplayStates,
                evaluatedNoneDisplays = evaluatedNoneDisplays,
            )
        }

    val textSize = when (element) {
        is Paragraph -> measureText(element.text, textMeasurer(element, resolvedTextStyle.font))
        is UiContainer -> null
    }

    val contents = buildList {
        beforePseudo
            ?.takeIf(MeasuredPseudoNode::displayed)
            ?.let { pseudo -> add(MeasuredContent.PseudoElement(pseudo)) }
        when (element) {
            is UiContainer -> children.forEach { child -> add(MeasuredContent.Element(child)) }
            is Paragraph -> add(MeasuredContent.Text(checkNotNull(textSize)))
        }
        afterPseudo
            ?.takeIf(MeasuredPseudoNode::displayed)
            ?.let { pseudo -> add(MeasuredContent.PseudoElement(pseudo)) }
    }

    val naturalSize = measureContents(contents, style.direction, style.gap)

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
        contents = contents,
        textStyle = resolvedTextStyle,
        displayed = true,
    )
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

private fun measurePseudoElement(
    element: UiElement,
    pseudoElement: UiPseudoElement,
    pseudoStyleProvider: () -> UiPseudoStyle,
    parentContentWidth: Float?,
    parentContentHeight: Float?,
    absoluteContainingWidth: Float?,
    absoluteContainingHeight: Float?,
    inheritedTextStyle: ResolvedUiTextStyle,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
    noneDisplayStates: MutableList<UiNoneDisplayState>,
    evaluatedNoneDisplays: Map<UiNoneDisplayKey, Boolean>,
): MeasuredPseudoNode {
    val pseudoStyle = pseudoStyleProvider()
    val style = pseudoStyle.style.resolveDefaults()
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
    val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
    val displayKey = UiNoneDisplayKey(element, pseudoElement)
    val noneDisplay = evaluatedNoneDisplays[displayKey] ?: style.noneDisplay()
    noneDisplayStates += UiNoneDisplayState(
        key = displayKey,
        predicate = style.noneDisplay,
        noneDisplay = noneDisplay,
    )

    if (noneDisplay) {
        return MeasuredPseudoNode(
            element = element,
            pseudoElement = pseudoElement,
            content = pseudoStyle.content,
            pseudoStyleProvider = pseudoStyleProvider,
            style = style,
            contentSize = UiSize(0f, 0f),
            textStyle = resolvedTextStyle,
            displayed = false,
        )
    }

    val naturalSize = when (val content = pseudoStyle.content) {
        is UiGeneratedContent.Text ->
            measureText(content.value, textMeasurer(element, resolvedTextStyle.font))

        UiGeneratedContent.EmptyBox -> UiSize(0f, 0f)
    }
    return MeasuredPseudoNode(
        element = element,
        pseudoElement = pseudoElement,
        content = pseudoStyle.content,
        pseudoStyleProvider = pseudoStyleProvider,
        style = style,
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
        textStyle = resolvedTextStyle,
        displayed = true,
    )
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

private fun measureContents(
    contents: List<MeasuredContent>,
    direction: UiDirection,
    gap: Float,
): UiSize {
    val normalContents = contents.filter { it.position != UiPosition.ABSOLUTE }
    val totalGap = gap * (normalContents.size - 1).coerceAtLeast(0)
    return when (direction) {
        UiDirection.VERTICAL -> UiSize(
            width = normalContents.maxOfOrNull { it.outerSize.width } ?: 0f,
            height = normalContents.sumOf { it.outerSize.height.toDouble() }.toFloat() + totalGap,
        )

        UiDirection.HORIZONTAL -> UiSize(
            width = normalContents.sumOf { it.outerSize.width.toDouble() }.toFloat() + totalGap,
            height = normalContents.maxOfOrNull { it.outerSize.height } ?: 0f,
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
    val placedContents = placeContents(positioned, contentBounds, descendantContainingBlock)
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
        children = placedContents.children,
        font = positioned.textStyle.font,
        color = positioned.textStyle.color,
        textShadow = positioned.textStyle.textShadow,
        displayed = positioned.displayed,
        beforePseudo = placedContents.beforePseudo,
        afterPseudo = placedContents.afterPseudo,
        textBounds = placedContents.textBounds,
    ).also { node ->
        node.styleProvider = positioned.styleProvider
    }
}

private data class PlacedContents(
    val children: List<UiLayoutNode>,
    val beforePseudo: UiPseudoLayoutNode?,
    val afterPseudo: UiPseudoLayoutNode?,
    val textBounds: UiRect?,
)

private fun placeContents(
    measured: MeasuredNode,
    contentBounds: UiRect,
    absoluteContainingBlock: UiRect,
): PlacedContents {
    if (measured.contents.isEmpty()) {
        return PlacedContents(emptyList(), null, null, null)
    }

    val style = measured.style
    val normalContents = measured.contents.filter { it.position != UiPosition.ABSOLUTE }
    val children = mutableListOf<UiLayoutNode>()
    var beforePseudo: UiPseudoLayoutNode? = null
    var afterPseudo: UiPseudoLayoutNode? = null
    var textBounds: UiRect? = null

    fun placeContent(content: MeasuredContent, left: Float, top: Float) {
        when (content) {
            is MeasuredContent.Element ->
                children += place(content.node, left, top, absoluteContainingBlock)

            is MeasuredContent.PseudoElement -> {
                val placed = placePseudoElement(
                    content.node,
                    left,
                    top,
                    absoluteContainingBlock,
                )
                when (content.node.pseudoElement) {
                    UiPseudoElement.BEFORE -> beforePseudo = placed
                    UiPseudoElement.AFTER -> afterPseudo = placed
                }
            }

            is MeasuredContent.Text -> textBounds = UiRect(
                left = left,
                top = top,
                width = content.size.width,
                height = content.size.height,
            )
        }
    }

    when (style.direction) {
        UiDirection.VERTICAL -> {
            val contentsHeight = normalContents
                .sumOf { it.outerSize.height.toDouble() }
                .toFloat() + style.gap * (normalContents.size - 1).coerceAtLeast(0)
            var top = contentBounds.top + alignedTop(
                availableHeight = contentBounds.height,
                itemHeight = contentsHeight,
                alignment = style.verticalAlignment,
            )
            var placedNormalContents = 0
            measured.contents.forEach { content ->
                val left = contentBounds.left + alignedLeft(
                    availableWidth = contentBounds.width,
                    itemWidth = content.outerSize.width,
                    alignment = style.horizontalAlignment,
                )
                placeContent(content, left, top)
                if (content.position != UiPosition.ABSOLUTE) {
                    placedNormalContents++
                    top += content.outerSize.height
                    if (placedNormalContents < normalContents.size) top += style.gap
                }
            }
        }

        UiDirection.HORIZONTAL -> {
            val contentsWidth = normalContents
                .sumOf { it.outerSize.width.toDouble() }
                .toFloat() + style.gap * (normalContents.size - 1).coerceAtLeast(0)
            var left = contentBounds.left + alignedLeft(
                availableWidth = contentBounds.width,
                itemWidth = contentsWidth,
                alignment = style.horizontalAlignment,
            )
            var placedNormalContents = 0
            measured.contents.forEach { content ->
                val top = contentBounds.top + alignedTop(
                    availableHeight = contentBounds.height,
                    itemHeight = content.outerSize.height,
                    alignment = style.verticalAlignment,
                )
                placeContent(content, left, top)
                if (content.position != UiPosition.ABSOLUTE) {
                    placedNormalContents++
                    left += content.outerSize.width
                    if (placedNormalContents < normalContents.size) left += style.gap
                }
            }
        }
    }

    return PlacedContents(children, beforePseudo, afterPseudo, textBounds)
}

private fun placePseudoElement(
    measured: MeasuredPseudoNode,
    staticOuterLeft: Float,
    staticOuterTop: Float,
    absoluteContainingBlock: UiRect?,
): UiPseudoLayoutNode {
    val positioned = if (measured.style.position == UiPosition.ABSOLUTE) {
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
    val style = positioned.style
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
    return UiPseudoLayoutNode(
        element = positioned.element,
        pseudoElement = positioned.pseudoElement,
        outerBounds = UiRect(
            left = outerLeft,
            top = outerTop,
            width = positioned.outerSize.width,
            height = positioned.outerSize.height,
        ),
        bounds = bounds,
        contentBounds = contentBounds,
        content = positioned.content,
        font = positioned.textStyle.font,
        displayed = positioned.displayed,
    ).also { node ->
        node.pseudoStyleProvider = positioned.pseudoStyleProvider
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

private fun MeasuredPseudoNode.stretchedTo(containingBlock: UiRect?): MeasuredPseudoNode {
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

private fun positionedOuterLeft(
    measured: MeasuredPseudoNode,
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
    measured: MeasuredPseudoNode,
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
