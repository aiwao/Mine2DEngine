package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont

/** Supplies the text measurements required by CSS intrinsic and line layout. */
interface UiTextMeasurer {
    val lineHeight: Float

    /** Distance from the top of a line box to its text baseline. */
    val baselineFromLineTop: Float
        get() = lineHeight

    fun width(text: String): Float
}

/** Text measurements backed by a loaded [Mine2DFont]. */
class Mine2DTextMeasurer(
    private val font: Mine2DFont,
) : UiTextMeasurer {
    override val lineHeight: Float
        get() = font.lineHeight

    override val baselineFromLineTop: Float
        get() = font.baselineFromLineTop

    override fun width(text: String): Float = font.width(text)
}

/**
 * CSS layout entry point.
 *
 * [viewport] is the initial containing block. A viewport is mandatory because CSS block
 * `width:auto`, percentages, absolute positioning, and flex free-space resolution all require an
 * available size supplied by the embedding application. The returned [UiLayout] can update it
 * later with [UiLayout.updateViewport].
 */
object LayoutEngine {
    /** Builds a root [Div] and lays it out inside [viewport]. */
    @JvmStatic
    fun layout(
        viewport: UiRect,
        rootStyle: UiStyle = UiStyle(),
        styleSheets: Iterable<StyleSheet> = emptyList(),
        content: Div.() -> Unit,
    ): UiLayout = layout(
        root = div(style = rootStyle, content = content),
        viewport = viewport,
        styleSheets = styleSheets,
    )

    /** Builds a root [Div], applies one author style sheet, and performs CSS layout. */
    @JvmStatic
    fun layout(
        viewport: UiRect,
        styleSheet: StyleSheet,
        rootStyle: UiStyle = UiStyle(),
        content: Div.() -> Unit,
    ): UiLayout = layout(viewport, rootStyle, listOf(styleSheet), content)

    /** Lays out [root] inside the initial containing block [viewport]. */
    @JvmStatic
    fun layout(
        root: UiElement,
        viewport: UiRect,
        styleSheets: Iterable<StyleSheet> = emptyList(),
    ): UiLayout = calculateLayout(root, viewport, styleSheets) { element, font ->
        Mine2DTextMeasurer(
            requireNotNull(font) {
                "${element.javaClass.simpleName} requires a font in its style or an ancestor style"
            },
        )
    }

    /** Lays out [root] after applying one author [styleSheet]. */
    @JvmStatic
    fun layout(
        root: UiElement,
        viewport: UiRect,
        styleSheet: StyleSheet,
    ): UiLayout = layout(root, viewport, listOf(styleSheet))
}

internal fun calculateLayout(
    root: UiElement,
    viewport: UiRect,
    textMeasurer: UiTextMeasurer,
    styleSheets: Iterable<StyleSheet> = emptyList(),
): UiLayout {
    validateTextMeasurer(textMeasurer)
    return calculateLayout(root, viewport, styleSheets) { _, _ -> textMeasurer }
}

private fun calculateLayout(
    root: UiElement,
    viewport: UiRect,
    styleSheets: Iterable<StyleSheet>,
    textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
): UiLayout {
    val sheets = styleSheets.toList()

    fun calculateSnapshot(
        currentViewport: UiRect,
        evaluatedDisplays: Map<UiDisplayKey, Boolean>,
    ): UiLayoutSnapshot {
        val displayStates = mutableListOf<UiDisplayState>()
        val boxTree = CssBoxTreeBuilder(
            root = root,
            styleSheets = sheets,
            displayStates = displayStates,
            evaluatedDisplays = evaluatedDisplays,
        ).build()
        val fragment = CssLayoutAlgorithm(textMeasurer).layout(boxTree, currentViewport)
        return UiLayoutSnapshot(
            root = fragment.toLayoutNode(),
            rootFragment = fragment.toPublicFragment(),
            displayStates = displayStates,
        )
    }

    val snapshot = calculateSnapshot(viewport, emptyMap())
    return UiLayout(snapshot, viewport, ::calculateSnapshot)
}

private fun CssFragment.toLayoutNode(): UiLayoutNode {
    val directPseudo = children.filter { child -> child.box.kind == CssBoxKind.PSEUDO }
    val childNodes = children
        .filterNot { child -> child.box.kind == CssBoxKind.PSEUDO }
        .flatMap(CssFragment::toLayoutNodes)
    val styledTextFragments = textFragments.map { fragment ->
        UiStyledTextLayoutFragment(
            fragment = UiTextLayoutFragment(fragment.text, fragment.bounds),
            textStyle = null,
        )
    } + children
        .filter { child -> child.box.kind == CssBoxKind.ANONYMOUS }
        .flatMap { child -> child.descendantStyledTextFragments(box.element) }
    val textLayoutFragments = styledTextFragments.map(UiStyledTextLayoutFragment::fragment)
    return UiLayoutNode(
        element = box.element,
        outerBounds = marginBox,
        bounds = borderBox,
        contentBounds = contentBox,
        children = childNodes,
        font = box.textStyle.font,
        color = box.textStyle.color,
        textShadow = box.textStyle.textShadow,
        displayed = !box.suppressed,
        beforePseudo = directPseudo
            .firstOrNull { child -> child.box.pseudoElement == UiPseudoElement.BEFORE }
            ?.toPseudoLayoutNode(),
        afterPseudo = directPseudo
            .firstOrNull { child -> child.box.pseudoElement == UiPseudoElement.AFTER }
            ?.toPseudoLayoutNode(),
        textBounds = unionBounds(textLayoutFragments.map(UiTextLayoutFragment::bounds)),
        textFragments = textLayoutFragments,
    ).also { node ->
        node.styleProvider = box.styleProvider
        node.styledTextFragments = styledTextFragments
    }
}

private fun CssFragment.descendantStyledTextFragments(
    ownerElement: UiElement,
): List<UiStyledTextLayoutFragment> =
    textFragments.map { fragment ->
        UiStyledTextLayoutFragment(
            fragment = UiTextLayoutFragment(fragment.text, fragment.bounds),
            textStyle = if (box.element === ownerElement && box.pseudoElement == null) {
                null
            } else {
                box.textStyle
            },
        )
    } + children
        .filter { child -> child.box.kind == CssBoxKind.ANONYMOUS }
        .flatMap { child -> child.descendantStyledTextFragments(ownerElement) }

private fun CssFragment.toLayoutNodes(): List<UiLayoutNode> = when (box.kind) {
    CssBoxKind.PRINCIPAL -> listOf(toLayoutNode())
    CssBoxKind.PSEUDO -> emptyList()
    CssBoxKind.ANONYMOUS -> children.flatMap(CssFragment::toLayoutNodes)
}

private fun CssFragment.toPseudoLayoutNode(): UiPseudoLayoutNode {
    val pseudo = requireNotNull(box.pseudoElement)
    val content = requireNotNull(box.generatedContent)
    return UiPseudoLayoutNode(
        element = box.element,
        pseudoElement = pseudo,
        outerBounds = marginBox,
        bounds = borderBox,
        contentBounds = contentBox,
        content = content,
        font = box.textStyle.font,
        displayed = !box.suppressed,
        textFragments = textFragments.map { fragment ->
            UiTextLayoutFragment(fragment.text, fragment.bounds)
        },
    ).also { node ->
        node.pseudoStyleProvider = requireNotNull(box.pseudoStyleProvider)
    }
}

private fun CssFragment.toPublicFragment(): UiBoxFragment = UiBoxFragment(
    element = box.element,
    pseudoElement = box.pseudoElement.takeIf { box.kind == CssBoxKind.PSEUDO },
    marginBox = marginBox,
    borderBox = borderBox,
    paddingBox = paddingBox,
    contentBox = contentBox,
    children = children.map(CssFragment::toPublicFragment),
    generatesBox = box.kind != CssBoxKind.ANONYMOUS && !box.suppressed,
)

private fun unionBounds(bounds: List<UiRect>): UiRect? {
    if (bounds.isEmpty()) return null
    val left = bounds.minOf(UiRect::left)
    val top = bounds.minOf(UiRect::top)
    val right = bounds.maxOf(UiRect::right)
    val bottom = bounds.maxOf(UiRect::bottom)
    return UiRect(left, top, right - left, bottom - top)
}

private fun validateTextMeasurer(textMeasurer: UiTextMeasurer) {
    require(textMeasurer.lineHeight.isFinite() && textMeasurer.lineHeight >= 0f) {
        "Text line height must be finite and non-negative: ${textMeasurer.lineHeight}"
    }
    require(
        textMeasurer.baselineFromLineTop.isFinite() &&
            textMeasurer.baselineFromLineTop >= 0f,
    ) {
        "Text baseline must be finite and non-negative: ${textMeasurer.baselineFromLineTop}"
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
