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
        get() = font.lineHeight.toFloat()

    override fun width(text: String): Float = font.width(text).toFloat()
}

/**
 * Measures and positions a tree of [Div], [Paragraph], and [Button] nodes.
 *
 * [left] and [top] passed to [layout] are the top-left coordinate of the root's outer box.
 * Text fonts are selected through [UiStyle.font] and remain owned by the caller.
 */
object LayoutEngine {
    /** Calculates the complete UI tree without issuing draw calls. */
    @JvmStatic
    fun layout(root: UiElement, left: Float = 0f, top: Float = 0f): UiLayout =
        calculateLayout(root, left, top) { element, font ->
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
): UiLayout {
    validateTextMeasurer(textMeasurer)
    return calculateLayout(root, left, top) { _, _ -> textMeasurer }
}

private fun calculateLayout(
    root: UiElement,
    left: Float,
    top: Float,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
): UiLayout {
    require(left.isFinite()) { "Left must be finite: $left" }
    require(top.isFinite()) { "Top must be finite: $top" }

    fun calculateSnapshot(
        snapshotLeft: Float,
        snapshotTop: Float,
        evaluatedNoneDisplays: Map<UiElement, Boolean>,
    ): UiLayoutSnapshot {
        val noneDisplayStates = mutableListOf<UiNoneDisplayState>()
        val measured = measure(
            element = root,
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
    val style: UiStyle,
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
    inheritedTextStyle: ResolvedUiTextStyle,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
    noneDisplayStates: MutableList<UiNoneDisplayState>,
    evaluatedNoneDisplays: Map<UiElement, Boolean>,
): MeasuredNode {
    val style = element.style
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
            contentSize = UiSize(0f, 0f),
            children = emptyList(),
            textStyle = resolvedTextStyle,
            displayed = false,
        )
    }

    val children = if (element is UiContainer) {
        element.children
            .map { child ->
                measure(
                    element = child,
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
        contentSize = UiSize(
            width = contentLength(
                specifiedLength = style.width,
                naturalLength = naturalSize.width,
                padding = style.padding.horizontal,
                boxSizing = style.boxSizing,
            ),
            height = contentLength(
                specifiedLength = style.height,
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
    val totalGap = gap * (children.size - 1).coerceAtLeast(0)
    return when (direction) {
        UiDirection.VERTICAL -> UiSize(
            width = children.maxOfOrNull { it.outerSize.width } ?: 0f,
            height = children.sumOf { it.outerSize.height.toDouble() }.toFloat() + totalGap,
        )

        UiDirection.HORIZONTAL -> UiSize(
            width = children.sumOf { it.outerSize.width.toDouble() }.toFloat() + totalGap,
            height = children.maxOfOrNull { it.outerSize.height } ?: 0f,
        )
    }
}

private fun place(measured: MeasuredNode, outerLeft: Float, outerTop: Float): UiLayoutNode {
    val style = measured.style
    if (!measured.displayed) {
        val emptyBounds = UiRect(outerLeft, outerTop, 0f, 0f)
        return UiLayoutNode(
            element = measured.element,
            outerBounds = emptyBounds,
            bounds = emptyBounds,
            contentBounds = emptyBounds,
            children = emptyList(),
            font = measured.textStyle.font,
            color = measured.textStyle.color,
            dropShadow = measured.textStyle.dropShadow,
            displayed = false,
        )
    }

    val bounds = UiRect(
        left = outerLeft + style.margin.left,
        top = outerTop + style.margin.top,
        width = measured.boundsSize.width,
        height = measured.boundsSize.height,
    )
    val contentBounds = UiRect(
        left = bounds.left + style.padding.left,
        top = bounds.top + style.padding.top,
        width = measured.contentSize.width,
        height = measured.contentSize.height,
    )

    val children = placeChildren(measured, contentBounds)
    return UiLayoutNode(
        element = measured.element,
        outerBounds = UiRect(outerLeft, outerTop, measured.outerSize.width, measured.outerSize.height),
        bounds = bounds,
        contentBounds = contentBounds,
        children = children,
        font = measured.textStyle.font,
        color = measured.textStyle.color,
        dropShadow = measured.textStyle.dropShadow,
        displayed = measured.displayed,
    )
}

private fun placeChildren(measured: MeasuredNode, contentBounds: UiRect): List<UiLayoutNode> {
    if (measured.children.isEmpty()) return emptyList()

    val style = measured.style
    return when (style.direction) {
        UiDirection.VERTICAL -> {
            val childrenHeight = measured.children
                .sumOf { it.outerSize.height.toDouble() }
                .toFloat() + style.gap * (measured.children.size - 1)
            var top = contentBounds.top + alignedTop(
                availableHeight = contentBounds.height,
                itemHeight = childrenHeight,
                alignment = style.verticalAlignment,
            )
            measured.children.map { child ->
                val left = contentBounds.left + alignedLeft(
                    availableWidth = contentBounds.width,
                    itemWidth = child.outerSize.width,
                    alignment = style.horizontalAlignment,
                )
                place(child, left, top).also { top += child.outerSize.height + style.gap }
            }
        }

        UiDirection.HORIZONTAL -> {
            val childrenWidth = measured.children
                .sumOf { it.outerSize.width.toDouble() }
                .toFloat() + style.gap * (measured.children.size - 1)
            var left = contentBounds.left + alignedLeft(
                availableWidth = contentBounds.width,
                itemWidth = childrenWidth,
                alignment = style.horizontalAlignment,
            )
            measured.children.map { child ->
                val top = contentBounds.top + alignedTop(
                    availableHeight = contentBounds.height,
                    itemHeight = child.outerSize.height,
                    alignment = style.verticalAlignment,
                )
                place(child, left, top).also { left += child.outerSize.width + style.gap }
            }
        }
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
