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

    val measured = measure(root, ResolvedUiTextStyle(), textMeasurer)
    return UiLayout(place(measured, left, top))
}

private data class MeasuredNode(
    val element: UiElement,
    val style: UiStyle,
    val contentSize: UiSize,
    val children: List<MeasuredNode>,
    val textStyle: ResolvedUiTextStyle,
) {
    val boundsSize: UiSize = UiSize(
        width = contentSize.width + style.padding.horizontal,
        height = contentSize.height + style.padding.vertical,
    )

    val outerSize: UiSize = UiSize(
        width = boundsSize.width + style.margin.horizontal,
        height = boundsSize.height + style.margin.vertical,
    )
}

private fun measure(
    element: UiElement,
    inheritedTextStyle: ResolvedUiTextStyle,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
): MeasuredNode {
    val style = element.style
    val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
    val children = if (element is UiContainer) {
        element.children.map { child -> measure(child, resolvedTextStyle, textMeasurer) }
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
            width = style.width ?: naturalSize.width,
            height = style.height ?: naturalSize.height,
        ),
        children = children,
        textStyle = resolvedTextStyle,
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
    )
}

private fun placeChildren(measured: MeasuredNode, contentBounds: UiRect): List<UiLayoutNode> {
    if (measured.children.isEmpty()) return emptyList()

    val style = measured.style
    return when (style.direction) {
        UiDirection.VERTICAL -> {
            var top = contentBounds.top
            measured.children.map { child ->
                val left = contentBounds.left + alignedLeft(
                    availableWidth = contentBounds.width,
                    itemWidth = child.outerSize.width,
                    alignment = style.alignment,
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
                alignment = style.alignment,
            )
            measured.children.map { child ->
                place(child, left, contentBounds.top).also { left += child.outerSize.width + style.gap }
            }
        }
    }
}

internal fun alignedLeft(
    availableWidth: Float,
    itemWidth: Float,
    alignment: UiAlignment,
): Float = when (alignment) {
    UiAlignment.LEFT -> 0f
    UiAlignment.CENTER -> (availableWidth - itemWidth) / 2f
    UiAlignment.RIGHT -> availableWidth - itemWidth
}

internal fun textLines(text: String): List<String> =
    text.split("\n", ignoreCase = false, limit = Int.MAX_VALUE)
