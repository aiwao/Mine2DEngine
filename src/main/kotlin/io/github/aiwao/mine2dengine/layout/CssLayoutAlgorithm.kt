package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class CssTextFragment(
    val text: String,
    val bounds: UiRect,
)

internal data class PendingAbsolute(
    val box: CssBox,
    val staticLeft: Float,
    val staticTop: Float,
)

/** A laid-out CSS box fragment. The current profile does not fragment principal boxes themselves. */
internal data class CssFragment(
    val box: CssBox,
    val marginBox: UiRect,
    val borderBox: UiRect,
    val paddingBox: UiRect,
    val contentBox: UiRect,
    val usedMargin: UsedEdges,
    val children: List<CssFragment>,
    val textFragments: List<CssTextFragment>,
    val pendingAbsolute: List<PendingAbsolute> = emptyList(),
    val baselineFromTop: Float? = null,
) {
    val flowOuterWidth: Float
        get() = borderBox.width + usedMargin.horizontal

    val flowOuterHeight: Float
        get() = borderBox.height + usedMargin.vertical
}

private data class ResolvedMargins(
    val top: Float?,
    val right: Float?,
    val bottom: Float?,
    val left: Float?,
) {
    fun withAutoAsZero(): UsedEdges = UsedEdges(
        top = top ?: 0f,
        right = right ?: 0f,
        bottom = bottom ?: 0f,
        left = left ?: 0f,
    )
}

private data class BoxMetrics(
    val margin: ResolvedMargins,
    val padding: UsedEdges,
)

private data class IntrinsicWidths(
    val min: Float,
    val max: Float,
) {
    init {
        require(min.isFinite() && min >= 0f)
        require(max.isFinite() && max >= min)
    }
}

private data class ContentLayout(
    val naturalWidth: Float,
    val naturalHeight: Float,
    val children: List<CssFragment>,
    val textFragments: List<CssTextFragment>,
    val pendingAbsolute: List<PendingAbsolute>,
    val baselineFromTop: Float? = null,
)

private data class BoxLayoutResult(
    val fragment: CssFragment,
    /** Static-flow dimensions, which are unaffected by relative positioning. */
    val flowOuterWidth: Float,
    val flowOuterHeight: Float,
)

/**
 * Implements the CSS layout profile used by Mine2DEngine: block/inline flow, intrinsic sizing,
 * positioned layout, and Flexbox Level 1.
 */
internal class CssLayoutAlgorithm(
    private val textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
) {
    private val intrinsicWidthCache = IdentityHashMap<CssBox, IntrinsicWidths>()

    fun layout(root: CssBox, viewport: UiRect): CssFragment {
        if (root.suppressed) {
            val empty = UiRect(viewport.left, viewport.top, 0f, 0f)
            return CssFragment(
                box = root,
                marginBox = empty,
                borderBox = empty,
                paddingBox = empty,
                contentBox = empty,
                usedMargin = UsedEdges(),
                children = emptyList(),
                textFragments = emptyList(),
            )
        }
        val constraints = ConstraintSpace(
            availableWidth = AvailableSize.Definite(viewport.width),
            availableHeight = AvailableSize.Definite(viewport.height),
            percentageWidth = viewport.width,
            percentageHeight = viewport.height,
        )
        val normal = layoutBox(
            box = root,
            constraints = constraints,
            staticMarginLeft = viewport.left,
            staticMarginTop = viewport.top,
            isRoot = true,
        ).fragment
        return resolveAbsoluteDescendants(normal, viewport)
    }

    private fun layoutBox(
        box: CssBox,
        constraints: ConstraintSpace,
        staticMarginLeft: Float,
        staticMarginTop: Float,
        isRoot: Boolean = false,
    ): BoxLayoutResult {
        val style = box.style
        val percentageWidth = constraints.percentageWidth
        val metrics = resolveMetrics(style, percentageWidth)
        val intrinsic = intrinsicWidths(box)
        val availableWidth = (constraints.availableWidth as? AvailableSize.Definite)?.value
        val display = box.displayBox

        var usedMargin = metrics.margin.withAutoAsZero()
        val specifiedContentWidth = constraints.forcedContentWidth ?: resolvePreferredSize(
            value = style.width,
            percentageBase = percentageWidth,
            intrinsic = intrinsic,
            available = availableWidth,
            padding = metrics.padding.horizontal,
            boxSizing = style.boxSizing,
        )
        val fillsAvailableWidth = display.outside == UiDisplayOutside.BLOCK &&
            !constraints.isFlexItem && !constraints.shrinkToFit && availableWidth != null
        val preferredContentWidth = when {
            constraints.forcedContentWidth != null -> constraints.forcedContentWidth
            specifiedContentWidth != null -> specifiedContentWidth
            fillsAvailableWidth -> (
                availableWidth - metrics.padding.horizontal -
                    (metrics.margin.left ?: 0f) - (metrics.margin.right ?: 0f)
                ).coerceAtLeast(0f)

            else -> fitContentWidth(intrinsic, availableWidth, metrics.padding.horizontal)
        }
        val contentWidth = clampWidth(
            box,
            preferredContentWidth,
            percentageWidth,
            intrinsic,
            metrics.padding,
        )

        if (!constraints.isFlexItem && (
            availableWidth != null && fillsAvailableWidth ||
                availableWidth != null && specifiedContentWidth != null
            )
        ) {
            usedMargin = resolveHorizontalAutoMargins(
                margins = metrics.margin,
                availableWidth = availableWidth,
                borderBoxWidth = contentWidth + metrics.padding.horizontal,
                fillAutoWidth = specifiedContentWidth == null &&
                    contentWidth == preferredContentWidth,
            )
        }

        val specifiedContentHeight = constraints.forcedContentHeight ?: resolvePreferredSize(
            value = style.height,
            percentageBase = constraints.percentageHeight,
            intrinsic = IntrinsicWidths(0f, 0f),
            available = (constraints.availableHeight as? AvailableSize.Definite)?.value,
            padding = metrics.padding.vertical,
            boxSizing = style.boxSizing,
        )

        val borderLeft = staticMarginLeft + usedMargin.left
        val borderTop = staticMarginTop + usedMargin.top
        val contentLeft = borderLeft + metrics.padding.left
        val contentTop = borderTop + metrics.padding.top
        val contentConstraints = ConstraintSpace(
            availableWidth = AvailableSize.Definite(contentWidth),
            availableHeight = specifiedContentHeight?.let(AvailableSize::Definite)
                ?: AvailableSize.Indefinite,
            percentageWidth = contentWidth,
            percentageHeight = specifiedContentHeight,
        )
        val contentLayout = if (box.element is InputControl && box.kind == CssBoxKind.PRINCIPAL) {
            val inputMetrics = box.element.intrinsicMetrics { measurer(box) }
            ContentLayout(
                naturalWidth = intrinsic.max,
                naturalHeight = inputMetrics.height,
                children = emptyList(),
                textFragments = emptyList(),
                pendingAbsolute = emptyList(),
                baselineFromTop = inputMetrics.baselineFromTop,
            )
        } else {
            when (display.inside) {
                UiDisplayInside.FLOW, UiDisplayInside.FLOW_ROOT -> layoutFlowContent(
                    box = box,
                    contentLeft = contentLeft,
                    contentTop = contentTop,
                    contentWidth = contentWidth,
                    constraints = contentConstraints,
                )

                UiDisplayInside.FLEX -> layoutFlexContent(
                    box = box,
                    contentLeft = contentLeft,
                    contentTop = contentTop,
                    contentWidth = contentWidth,
                    specifiedContentHeight = specifiedContentHeight,
                    constraints = contentConstraints,
                )
            }
        }
        var contentHeight = specifiedContentHeight ?: contentLayout.naturalHeight
        contentHeight = clampHeight(
            box = box,
            contentHeight = contentHeight,
            percentageBase = constraints.percentageHeight,
            padding = metrics.padding,
            naturalHeight = contentLayout.naturalHeight,
        )

        // A definite cross size can change flex line stretching and therefore item geometry. Run
        // the flex context once more with its final clamped height when necessary.
        val finalContentLayout = if (
            box.element !is InputControl &&
            display.inside == UiDisplayInside.FLEX &&
            contentHeight != specifiedContentHeight
        ) {
            layoutFlexContent(
                box = box,
                contentLeft = contentLeft,
                contentTop = contentTop,
                contentWidth = contentWidth,
                specifiedContentHeight = contentHeight,
                constraints = contentConstraints.copy(
                    availableHeight = AvailableSize.Definite(contentHeight),
                    percentageHeight = contentHeight,
                ),
            )
        } else {
            contentLayout
        }

        val borderWidth = contentWidth + metrics.padding.horizontal
        val borderHeight = contentHeight + metrics.padding.vertical
        val borderBox = UiRect(borderLeft, borderTop, borderWidth, borderHeight)
        val contentBox = UiRect(contentLeft, contentTop, contentWidth, contentHeight)
        val baselineFromTop = if (box.element is InputControl && box.kind == CssBoxKind.PRINCIPAL) {
            val inputMetrics = box.element.intrinsicMetrics { measurer(box) }
            metrics.padding.top +
                (contentHeight - inputMetrics.height) / 2f +
                inputMetrics.baselineFromTop
        } else {
            finalContentLayout.baselineFromTop?.let { baseline ->
                metrics.padding.top + baseline
            }
        }
        var fragment = CssFragment(
            box = box,
            marginBox = safeMarginRect(
                staticMarginLeft,
                staticMarginTop,
                borderWidth + usedMargin.horizontal,
                borderHeight + usedMargin.vertical,
            ),
            borderBox = borderBox,
            paddingBox = borderBox,
            contentBox = contentBox,
            usedMargin = usedMargin,
            children = finalContentLayout.children,
            textFragments = finalContentLayout.textFragments,
            pendingAbsolute = finalContentLayout.pendingAbsolute,
            baselineFromTop = baselineFromTop,
        )
        if (!isRoot && style.position == UiPosition.RELATIVE) {
            val (offsetX, offsetY) = relativeOffset(style, constraints)
            fragment = fragment.translated(offsetX, offsetY)
        }
        return BoxLayoutResult(
            fragment = fragment,
            flowOuterWidth = borderWidth + usedMargin.horizontal,
            flowOuterHeight = borderHeight + usedMargin.vertical,
        )
    }

    private fun resolveMetrics(style: ResolvedUiStyle, percentageWidth: Float?): BoxMetrics =
        BoxMetrics(
            margin = ResolvedMargins(
                top = resolveMargin(style.margin.top, percentageWidth),
                right = resolveMargin(style.margin.right, percentageWidth),
                bottom = resolveMargin(style.margin.bottom, percentageWidth),
                left = resolveMargin(style.margin.left, percentageWidth),
            ),
            padding = UsedEdges(
                top = style.padding.top.resolve(percentageWidth) ?: 0f,
                right = style.padding.right.resolve(percentageWidth) ?: 0f,
                bottom = style.padding.bottom.resolve(percentageWidth) ?: 0f,
                left = style.padding.left.resolve(percentageWidth) ?: 0f,
            ),
        )

    private fun resolveMargin(value: UiMarginValue, percentageWidth: Float?): Float? = when (value) {
        UiMarginValue.Auto -> null
        is UiLength -> value.resolve(percentageWidth) ?: 0f
    }

    private fun resolvePreferredSize(
        value: UiSizeValue,
        percentageBase: Float?,
        intrinsic: IntrinsicWidths,
        available: Float?,
        padding: Float,
        boxSizing: UiBoxSizing,
    ): Float? {
        val specified = when (value) {
            UiSizeValue.Auto, UiSizeValue.None -> return null
            UiSizeValue.MinContent -> intrinsic.min
            UiSizeValue.MaxContent -> intrinsic.max
            is UiSizeValue.FitContent -> {
                val limit = value.limit?.resolve(percentageBase) ?: available
                limit?.let { max(intrinsic.min, min(intrinsic.max, (it - padding).coerceAtLeast(0f))) }
                    ?: intrinsic.max
            }

            is UiLength -> value.resolve(percentageBase) ?: return null
        }
        return when (boxSizing) {
            UiBoxSizing.CONTENT_BOX -> specified.coerceAtLeast(0f)
            UiBoxSizing.BORDER_BOX -> (specified - padding).coerceAtLeast(0f)
        }
    }

    private fun fitContentWidth(
        intrinsic: IntrinsicWidths,
        availableWidth: Float?,
        padding: Float,
    ): Float = availableWidth?.let { available ->
        max(intrinsic.min, min(intrinsic.max, (available - padding).coerceAtLeast(0f)))
    } ?: intrinsic.max

    private fun clampWidth(
        box: CssBox,
        width: Float,
        percentageBase: Float?,
        intrinsic: IntrinsicWidths,
        padding: UsedEdges,
    ): Float {
        val minWidth = resolvePreferredSize(
            box.style.minWidth,
            percentageBase,
            intrinsic,
            null,
            padding.horizontal,
            box.style.boxSizing,
        ) ?: if (box.style.minWidth == UiSizeValue.AUTO && box.style.flexShrink > 0f) {
            0f
        } else {
            0f
        }
        val maxWidth = resolvePreferredSize(
            box.style.maxWidth,
            percentageBase,
            intrinsic,
            null,
            padding.horizontal,
            box.style.boxSizing,
        ) ?: Float.POSITIVE_INFINITY
        return width.coerceIn(minWidth.coerceAtMost(maxWidth), maxWidth)
    }

    private fun clampHeight(
        box: CssBox,
        contentHeight: Float,
        percentageBase: Float?,
        padding: UsedEdges,
        naturalHeight: Float,
    ): Float {
        val intrinsic = IntrinsicWidths(naturalHeight, naturalHeight)
        val minHeight = resolvePreferredSize(
            box.style.minHeight,
            percentageBase,
            intrinsic,
            null,
            padding.vertical,
            box.style.boxSizing,
        ) ?: 0f
        val maxHeight = resolvePreferredSize(
            box.style.maxHeight,
            percentageBase,
            intrinsic,
            null,
            padding.vertical,
            box.style.boxSizing,
        ) ?: Float.POSITIVE_INFINITY
        return contentHeight.coerceIn(minHeight.coerceAtMost(maxHeight), maxHeight)
    }

    private fun resolveHorizontalAutoMargins(
        margins: ResolvedMargins,
        availableWidth: Float,
        borderBoxWidth: Float,
        fillAutoWidth: Boolean,
    ): UsedEdges {
        if (fillAutoWidth) {
            return margins.withAutoAsZero()
        }
        val fixed = (margins.left ?: 0f) + (margins.right ?: 0f)
        val remaining = availableWidth - borderBoxWidth - fixed
        val left = margins.left
        val right = margins.right
        val usedLeft: Float
        val usedRight: Float
        when {
            left == null && right == null -> {
                if (remaining >= 0f) {
                    usedLeft = remaining / 2f
                    usedRight = remaining - usedLeft
                } else {
                    usedLeft = 0f
                    usedRight = remaining
                }
            }

            left == null -> {
                usedLeft = remaining
                usedRight = right ?: 0f
            }

            right == null -> {
                usedLeft = left
                usedRight = remaining
            }

            else -> {
                usedLeft = left
                usedRight = right + remaining
            }
        }
        return UsedEdges(
            top = margins.top ?: 0f,
            right = usedRight,
            bottom = margins.bottom ?: 0f,
            left = usedLeft,
        )
    }

    private fun safeMarginRect(left: Float, top: Float, width: Float, height: Float): UiRect =
        UiRect(left, top, width.coerceAtLeast(0f), height.coerceAtLeast(0f))

    private sealed interface FlowEntry {
        data class Box(
            val box: CssBox,
        ) : FlowEntry

        data class Text(
            val value: String,
        ) : FlowEntry
    }

    private fun contentEntries(box: CssBox): List<FlowEntry> {
        if (box.text == null) return box.children.map(FlowEntry::Box)
        val before = box.children.filter { it.pseudoElement == UiPseudoElement.BEFORE }
        val rest = box.children.filter { it.pseudoElement != UiPseudoElement.BEFORE }
        return before.map(FlowEntry::Box) + FlowEntry.Text(box.text) + rest.map(FlowEntry::Box)
    }

    private fun layoutFlowContent(
        box: CssBox,
        contentLeft: Float,
        contentTop: Float,
        contentWidth: Float,
        constraints: ConstraintSpace,
    ): ContentLayout {
        val children = mutableListOf<CssFragment>()
        val textFragments = mutableListOf<CssTextFragment>()
        val pendingAbsolute = mutableListOf<PendingAbsolute>()
        val inlineEntries = mutableListOf<FlowEntry>()
        var cursorTop = contentTop
        var previousBottomMargin: Float? = null
        var maximumContentWidth = 0f
        var firstBaseline: Float? = null

        fun flushInlineEntries() {
            if (inlineEntries.isEmpty()) return
            val inline = layoutInlineRun(
                owner = box,
                entries = inlineEntries.toList(),
                contentLeft = contentLeft,
                contentTop = cursorTop,
                contentWidth = contentWidth,
                constraints = constraints,
            )
            children += inline.children
            textFragments += inline.textFragments
            pendingAbsolute += inline.pendingAbsolute
            maximumContentWidth = max(maximumContentWidth, inline.naturalWidth)
            if (firstBaseline == null && inline.baselineFromTop != null) {
                firstBaseline = cursorTop - contentTop + inline.baselineFromTop
            }
            cursorTop += inline.naturalHeight
            previousBottomMargin = null
            inlineEntries.clear()
        }

        contentEntries(box).forEach { entry ->
            when (entry) {
                is FlowEntry.Text -> inlineEntries += entry
                is FlowEntry.Box -> {
                    val child = entry.box
                    if (child.isAbsolutelyPositioned) {
                        // Out-of-flow boxes do not split an inline formatting context. The current
                        // implementation uses the start of the current line as their static point.
                        pendingAbsolute += PendingAbsolute(child, contentLeft, cursorTop)
                    } else if (child.displayBox.outside == UiDisplayOutside.INLINE) {
                        inlineEntries += entry
                    } else {
                        flushInlineEntries()
                        val childMetrics = resolveMetrics(child.style, contentWidth)
                        val childTopMargin = childMetrics.margin.top ?: 0f
                        val staticMarginTop = previousBottomMargin?.let { previous ->
                            cursorTop + collapseMargins(previous, childTopMargin) - childTopMargin
                        } ?: cursorTop
                        val childResult = layoutBox(
                            box = child,
                            constraints = ConstraintSpace(
                                availableWidth = AvailableSize.Definite(contentWidth),
                                availableHeight = AvailableSize.Indefinite,
                                percentageWidth = contentWidth,
                                percentageHeight = constraints.percentageHeight,
                            ),
                            staticMarginLeft = contentLeft,
                            staticMarginTop = staticMarginTop,
                        )
                        children += childResult.fragment
                        val used = childResult.fragment.usedMargin
                        val staticBorderBottom = staticMarginTop + used.top +
                            childResult.fragment.borderBox.height
                        cursorTop = staticBorderBottom
                        previousBottomMargin = used.bottom
                        maximumContentWidth = max(
                            maximumContentWidth,
                            childResult.flowOuterWidth.coerceAtLeast(0f),
                        )
                        if (firstBaseline == null) {
                            childResult.fragment.baselineFromTop?.let { childBaseline ->
                                firstBaseline = childResult.fragment.borderBox.top - contentTop +
                                    childBaseline
                            }
                        }
                    }
                }
            }
        }
        flushInlineEntries()
        val naturalBottom = cursorTop + (previousBottomMargin ?: 0f)
        return ContentLayout(
            naturalWidth = maximumContentWidth,
            naturalHeight = (naturalBottom - contentTop).coerceAtLeast(0f),
            children = children,
            textFragments = textFragments,
            pendingAbsolute = pendingAbsolute,
            baselineFromTop = firstBaseline,
        )
    }

    private fun collapseMargins(first: Float, second: Float): Float {
        val positive = max(first.coerceAtLeast(0f), second.coerceAtLeast(0f))
        val negative = min(first.coerceAtMost(0f), second.coerceAtMost(0f))
        return positive + negative
    }

    private sealed interface InlineAtom {
        val width: Float
        val height: Float
        val baseline: Float

        data class Text(
            val value: String,
            override val width: Float,
            override val height: Float,
            override val baseline: Float,
            val mayWrapBefore: Boolean,
        ) : InlineAtom

        data class Box(
            val result: BoxLayoutResult,
        ) : InlineAtom {
            override val width: Float
                get() = result.flowOuterWidth.coerceAtLeast(0f)

            override val height: Float
                get() = result.flowOuterHeight.coerceAtLeast(0f)

            override val baseline: Float
                get() = (
                    result.fragment.usedMargin.top +
                        (result.fragment.baselineFromTop ?: result.fragment.borderBox.height)
                    ).coerceAtLeast(0f)
        }

        data object Break : InlineAtom {
            override val width: Float = 0f
            override val height: Float = 0f
            override val baseline: Float = 0f
        }
    }

    private data class InlineLine(
        val atoms: List<InlineAtom>,
        val width: Float,
        val height: Float,
        val baseline: Float,
    )

    private fun layoutInlineRun(
        owner: CssBox,
        entries: List<FlowEntry>,
        contentLeft: Float,
        contentTop: Float,
        contentWidth: Float,
        constraints: ConstraintSpace,
    ): ContentLayout {
        val atoms = buildList {
            entries.forEach { entry ->
                when (entry) {
                    is FlowEntry.Box -> {
                        if (entry.box.isAbsolutelyPositioned) return@forEach
                        add(
                            InlineAtom.Box(
                                layoutBox(
                                    box = entry.box,
                                    constraints = ConstraintSpace(
                                        availableWidth = AvailableSize.Definite(contentWidth),
                                        availableHeight = AvailableSize.Indefinite,
                                        percentageWidth = contentWidth,
                                        percentageHeight = constraints.percentageHeight,
                                        shrinkToFit = true,
                                    ),
                                    staticMarginLeft = 0f,
                                    staticMarginTop = 0f,
                                ),
                            ),
                        )
                    }

                    is FlowEntry.Text -> addAll(textAtoms(owner, entry.value))
                }
            }
        }
        if (atoms.isEmpty()) {
            return ContentLayout(0f, 0f, emptyList(), emptyList(), emptyList())
        }

        val wraps = owner.textStyle.whiteSpace == UiWhiteSpace.NORMAL
        val lines = mutableListOf<InlineLine>()
        val lineAtoms = mutableListOf<InlineAtom>()
        var lineWidth = 0f
        var lineAscent = 0f
        var lineDescent = 0f

        fun flushLine(forceEmpty: Boolean = false) {
            if (lineAtoms.isEmpty() && !forceEmpty) return
            val strut = measurer(owner)
            val ascent = max(lineAscent, strut.baselineFromLineTop)
            val descent = max(lineDescent, strut.lineHeight - strut.baselineFromLineTop)
            lines += InlineLine(
                atoms = lineAtoms.toList(),
                width = lineWidth,
                height = ascent + descent,
                baseline = ascent,
            )
            lineAtoms.clear()
            lineWidth = 0f
            lineAscent = 0f
            lineDescent = 0f
        }

        atoms.forEach { atom ->
            if (atom == InlineAtom.Break) {
                flushLine(forceEmpty = true)
                return@forEach
            }
            var actualAtom = atom
            if (actualAtom is InlineAtom.Text && lineAtoms.isEmpty() &&
                actualAtom.value.startsWith(' ')
            ) {
                val withoutSpace = actualAtom.value.drop(1)
                actualAtom = actualAtom.copy(
                    value = withoutSpace,
                    width = measurer(owner).width(withoutSpace),
                    mayWrapBefore = false,
                )
            }
            if (
                wraps && lineAtoms.isNotEmpty() &&
                lineWidth + actualAtom.width > contentWidth &&
                (actualAtom !is InlineAtom.Text || actualAtom.mayWrapBefore)
            ) {
                flushLine()
                if (actualAtom is InlineAtom.Text && actualAtom.value.startsWith(' ')) {
                    val withoutSpace = actualAtom.value.drop(1)
                    actualAtom = actualAtom.copy(
                        value = withoutSpace,
                        width = measurer(owner).width(withoutSpace),
                        mayWrapBefore = false,
                    )
                }
            }
            lineAtoms += actualAtom
            lineWidth += actualAtom.width
            lineAscent = max(lineAscent, actualAtom.baseline)
            lineDescent = max(lineDescent, actualAtom.height - actualAtom.baseline)
        }
        flushLine()

        val children = mutableListOf<CssFragment>()
        val textFragments = mutableListOf<CssTextFragment>()
        var lineTop = contentTop
        lines.forEach { line ->
            var x = contentLeft + textAlignmentOffset(owner.textStyle.textAlign, contentWidth, line.width)
            line.atoms.forEach { atom ->
                val atomTop = lineTop + line.baseline - atom.baseline
                when (atom) {
                    is InlineAtom.Text -> if (atom.value.isNotEmpty()) {
                        textFragments += CssTextFragment(
                            text = atom.value,
                            bounds = UiRect(x, atomTop, atom.width, atom.height),
                        )
                    }

                    is InlineAtom.Box -> {
                        val fragment = atom.result.fragment
                        val positioned = fragment.translated(
                            x - fragment.marginBox.left,
                            atomTop - fragment.marginBox.top,
                        )
                        children += positioned
                    }

                    InlineAtom.Break -> Unit
                }
                x += atom.width
            }
            lineTop += line.height
        }
        return ContentLayout(
            naturalWidth = lines.maxOfOrNull(InlineLine::width) ?: 0f,
            naturalHeight = lineTop - contentTop,
            children = children,
            textFragments = textFragments,
            pendingAbsolute = emptyList(),
            baselineFromTop = lines.firstOrNull()?.baseline,
        )
    }

    private fun textAtoms(owner: CssBox, text: String): List<InlineAtom> {
        if (text.isEmpty()) return emptyList()
        val measurer = measurer(owner)
        return when (owner.textStyle.whiteSpace) {
            UiWhiteSpace.PRE -> buildList {
                text.split('\n').forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        add(
                            InlineAtom.Text(
                                value = line,
                                width = measurer.width(line),
                                height = measurer.lineHeight,
                                baseline = measurer.baselineFromLineTop,
                                mayWrapBefore = false,
                            ),
                        )
                    }
                    if (index < text.count { it == '\n' }) add(InlineAtom.Break)
                }
            }

            UiWhiteSpace.NORMAL, UiWhiteSpace.NOWRAP -> {
                val normalized = text.replace(Regex("\\s+"), " ").trim()
                if (normalized.isEmpty()) {
                    emptyList()
                } else {
                    normalized.split(' ').mapIndexed { index, word ->
                        val rendered = if (index == 0) word else " $word"
                        InlineAtom.Text(
                            value = rendered,
                            width = measurer.width(rendered),
                            height = measurer.lineHeight,
                            baseline = measurer.baselineFromLineTop,
                            mayWrapBefore = index > 0,
                        )
                    }
                }
            }
        }
    }

    private fun textAlignmentOffset(
        alignment: UiTextAlign,
        availableWidth: Float,
        lineWidth: Float,
    ): Float = when (alignment) {
        UiTextAlign.START, UiTextAlign.LEFT -> 0f
        UiTextAlign.END, UiTextAlign.RIGHT -> availableWidth - lineWidth
        UiTextAlign.CENTER -> (availableWidth - lineWidth) / 2f
    }

    private fun measurer(box: CssBox): UiTextMeasurer = textMeasurer(
        box.element,
        box.textStyle.font,
    ).also(::validateTextMeasurer)

    private fun validateTextMeasurer(measurer: UiTextMeasurer) {
        require(measurer.lineHeight.isFinite() && measurer.lineHeight >= 0f) {
            "Text line height must be finite and non-negative: ${measurer.lineHeight}"
        }
        require(
            measurer.baselineFromLineTop.isFinite() && measurer.baselineFromLineTop >= 0f,
        ) {
            "Text baseline must be finite and non-negative: ${measurer.baselineFromLineTop}"
        }
    }

    private fun intrinsicWidths(box: CssBox): IntrinsicWidths =
        intrinsicWidthCache[box] ?: calculateIntrinsicWidths(box).also { widths ->
            intrinsicWidthCache[box] = widths
        }

    private fun calculateIntrinsicWidths(box: CssBox): IntrinsicWidths {
        if (box.element is InputControl && box.kind == CssBoxKind.PRINCIPAL) {
            val width = box.element.intrinsicMetrics { measurer(box) }.width
            return IntrinsicWidths(width, width)
        }
        val metrics = resolveMetrics(box.style, percentageWidth = null)
        var inlineMin = 0f
        var inlineMax = 0f
        var contentMin = 0f
        var contentMax = 0f

        fun flushInline() {
            contentMin = max(contentMin, inlineMin)
            contentMax = max(contentMax, inlineMax)
            inlineMin = 0f
            inlineMax = 0f
        }

        val entries = contentEntries(box)
        if (box.displayBox.inside == UiDisplayInside.FLEX) {
            val contributions = flexChildren(box)
                .filterNot(CssBox::isAbsolutelyPositioned)
                .map(::outerIntrinsicWidths)
            val mainGap = when (box.style.flexDirection) {
                UiFlexDirection.ROW, UiFlexDirection.ROW_REVERSE ->
                    box.style.columnGap.resolve(null) ?: 0f

                UiFlexDirection.COLUMN, UiFlexDirection.COLUMN_REVERSE ->
                    box.style.rowGap.resolve(null) ?: 0f
            }
            val totalGap = mainGap * (contributions.size - 1).coerceAtLeast(0)
            when (box.style.flexDirection) {
                UiFlexDirection.ROW, UiFlexDirection.ROW_REVERSE -> {
                    contentMax = contributions.sumOf { it.max.toDouble() }.toFloat() + totalGap
                    contentMin = if (box.style.flexWrap == UiFlexWrap.NOWRAP) {
                        contributions.sumOf { it.min.toDouble() }.toFloat() + totalGap
                    } else {
                        contributions.maxOfOrNull(IntrinsicWidths::min) ?: 0f
                    }
                }

                UiFlexDirection.COLUMN, UiFlexDirection.COLUMN_REVERSE -> {
                    contentMin = contributions.maxOfOrNull(IntrinsicWidths::min) ?: 0f
                    contentMax = contributions.maxOfOrNull(IntrinsicWidths::max) ?: 0f
                }
            }
        } else {
            entries.forEach { entry ->
                when (entry) {
                    is FlowEntry.Text -> {
                        val textWidths = intrinsicTextWidths(box, entry.value)
                        inlineMin = max(inlineMin, textWidths.min)
                        inlineMax += textWidths.max
                    }

                    is FlowEntry.Box -> {
                        if (entry.box.isAbsolutelyPositioned) return@forEach
                        val contribution = outerIntrinsicWidths(entry.box)
                        if (entry.box.displayBox.outside == UiDisplayOutside.BLOCK) {
                            flushInline()
                            contentMin = max(contentMin, contribution.min)
                            contentMax = max(contentMax, contribution.max)
                        } else {
                            inlineMin = max(inlineMin, contribution.min)
                            inlineMax += contribution.max
                        }
                    }
                }
            }
            flushInline()
        }

        val natural = IntrinsicWidths(contentMin, max(contentMin, contentMax))
        val specified = resolvePreferredSize(
            value = box.style.width,
            percentageBase = null,
            intrinsic = natural,
            available = null,
            padding = metrics.padding.horizontal,
            boxSizing = box.style.boxSizing,
        )
        var usedMin = specified ?: natural.min
        var usedMax = specified ?: natural.max
        val minimum = resolvePreferredSize(
            value = box.style.minWidth,
            percentageBase = null,
            intrinsic = natural,
            available = null,
            padding = metrics.padding.horizontal,
            boxSizing = box.style.boxSizing,
        )
        val maximum = resolvePreferredSize(
            value = box.style.maxWidth,
            percentageBase = null,
            intrinsic = natural,
            available = null,
            padding = metrics.padding.horizontal,
            boxSizing = box.style.boxSizing,
        )
        if (maximum != null) {
            usedMin = min(usedMin, maximum)
            usedMax = min(usedMax, maximum)
        }
        if (minimum != null) {
            usedMin = max(usedMin, minimum)
            usedMax = max(usedMax, minimum)
        }
        return IntrinsicWidths(usedMin.coerceAtLeast(0f), max(usedMin, usedMax).coerceAtLeast(0f))
    }

    private fun outerIntrinsicWidths(box: CssBox): IntrinsicWidths {
        val content = intrinsicWidths(box)
        val metrics = resolveMetrics(box.style, percentageWidth = null)
        val fixedMargins = metrics.margin.withAutoAsZero().horizontal
        val extras = metrics.padding.horizontal + fixedMargins
        return IntrinsicWidths(
            min = (content.min + extras).coerceAtLeast(0f),
            max = max(content.min + extras, content.max + extras).coerceAtLeast(0f),
        )
    }

    private fun intrinsicTextWidths(box: CssBox, text: String): IntrinsicWidths {
        if (text.isEmpty()) return IntrinsicWidths(0f, 0f)
        val measurer = measurer(box)
        return when (box.textStyle.whiteSpace) {
            UiWhiteSpace.PRE -> {
                val max = text.split('\n').maxOfOrNull(measurer::width) ?: 0f
                IntrinsicWidths(max, max)
            }

            UiWhiteSpace.NOWRAP -> {
                val normalized = text.replace(Regex("\\s+"), " ").trim()
                val width = measurer.width(normalized)
                IntrinsicWidths(width, width)
            }

            UiWhiteSpace.NORMAL -> {
                val normalized = text.replace(Regex("\\s+"), " ").trim()
                val maximum = measurer.width(normalized)
                val minimum = normalized.split(' ')
                    .maxOfOrNull(measurer::width)
                    ?: 0f
                IntrinsicWidths(minimum, max(minimum, maximum))
            }
        }
    }

    private fun flexChildren(box: CssBox): List<CssBox> {
        val entries = contentEntries(box)
        return entries.map { entry ->
            when (entry) {
                is FlowEntry.Box -> entry.box.blockified()
                is FlowEntry.Text -> anonymousFlexTextBox(box, entry.value)
            }
        }
    }

    private fun anonymousFlexTextBox(owner: CssBox, text: String): CssBox {
        val anonymousStyle = owner.style.copy(
            color = null,
            backgroundColor = null,
            backgroundMaterial = null,
            borderRadius = UiBorderRadii.ZERO,
            margin = UiMargins(),
            padding = UiPaddings(),
            display = UiDisplay.BLOCK,
            width = UiSizeValue.AUTO,
            height = UiSizeValue.AUTO,
            minWidth = UiSizeValue.AUTO,
            minHeight = UiSizeValue.AUTO,
            maxWidth = UiSizeValue.NONE,
            maxHeight = UiSizeValue.NONE,
            position = UiPosition.STATIC,
            flexGrow = 0f,
            flexShrink = 1f,
            flexBasis = UiFlexBasis.AUTO,
            order = 0,
            boxShadow = null,
            textShadow = owner.textStyle.textShadow,
            dropShadow = null,
        )
        return CssBox(
            kind = CssBoxKind.ANONYMOUS,
            element = owner.element,
            style = anonymousStyle,
            styleProvider = { anonymousStyle },
            textStyle = owner.textStyle,
            text = text,
        )
    }

    private data class FlexAxis(
        val isRow: Boolean,
        val isMainReverse: Boolean,
    )

    private class FlexItemPlan(
        val box: CssBox,
        val metrics: BoxMetrics,
        val baseMainSize: Float,
        val minMainSize: Float,
        val maxMainSize: Float,
        val sourceIndex: Int,
    ) {
        val hypotheticalMainSize: Float = baseMainSize.coerceIn(minMainSize, maxMainSize)
        var targetMainSize: Float = hypotheticalMainSize
        lateinit var result: BoxLayoutResult
        var usedMargin: UsedEdges = metrics.margin.withAutoAsZero()
    }

    private data class FlexLine(
        val items: List<FlexItemPlan>,
        var crossSize: Float = 0f,
        var crossOffset: Float = 0f,
        var baseline: Float? = null,
    )

    private fun layoutFlexContent(
        box: CssBox,
        contentLeft: Float,
        contentTop: Float,
        contentWidth: Float,
        specifiedContentHeight: Float?,
        constraints: ConstraintSpace,
    ): ContentLayout {
        val axis = when (box.style.flexDirection) {
            UiFlexDirection.ROW -> FlexAxis(isRow = true, isMainReverse = false)
            UiFlexDirection.ROW_REVERSE -> FlexAxis(isRow = true, isMainReverse = true)
            UiFlexDirection.COLUMN -> FlexAxis(isRow = false, isMainReverse = false)
            UiFlexDirection.COLUMN_REVERSE -> FlexAxis(isRow = false, isMainReverse = true)
        }
        val allChildren = flexChildren(box)
        val absoluteChildren = allChildren.filter(CssBox::isAbsolutelyPositioned)
        val orderedChildren = allChildren
            .filterNot(CssBox::isAbsolutelyPositioned)
            .sortedWith(compareBy<CssBox> { it.style.order }.thenBy(CssBox::sourceIndex))
        if (orderedChildren.isEmpty()) {
            return ContentLayout(
                naturalWidth = 0f,
                naturalHeight = 0f,
                children = emptyList(),
                textFragments = emptyList(),
                pendingAbsolute = absoluteChildren.map { child ->
                    PendingAbsolute(child, contentLeft, contentTop)
                },
            )
        }

        val availableMain = if (axis.isRow) contentWidth else specifiedContentHeight
        val mainGap = if (axis.isRow) {
            box.style.columnGap.resolve(contentWidth) ?: 0f
        } else {
            box.style.rowGap.resolve(specifiedContentHeight) ?: 0f
        }
        val crossGap = if (axis.isRow) {
            box.style.rowGap.resolve(specifiedContentHeight) ?: 0f
        } else {
            box.style.columnGap.resolve(contentWidth) ?: 0f
        }

        val plans = orderedChildren.mapIndexed { index, child ->
            createFlexItemPlan(
                box = child,
                axis = axis,
                containerWidth = contentWidth,
                containerHeight = specifiedContentHeight,
                availableMain = availableMain,
                containerAlignItems = box.style.alignItems,
                sourceIndex = index,
            )
        }
        val lines = collectFlexLines(
            plans = plans,
            availableMain = availableMain,
            mainGap = mainGap,
            wrap = box.style.flexWrap != UiFlexWrap.NOWRAP,
            axis = axis,
        )
        val usedMain = availableMain ?: lines.maxOfOrNull { line ->
            line.items.sumOf { item -> itemOuterMain(item, axis, item.targetMainSize).toDouble() }
                .toFloat() + mainGap * (line.items.size - 1).coerceAtLeast(0)
        } ?: 0f

        lines.forEach { line ->
            resolveFlexibleLengths(line, usedMain, mainGap, axis)
            line.items.forEach { item ->
                item.result = layoutFlexItem(
                    item = item,
                    axis = axis,
                    targetMain = item.targetMainSize,
                    forcedCross = null,
                    containerWidth = contentWidth,
                    containerHeight = specifiedContentHeight,
                )
            }
            line.crossSize = line.items.maxOfOrNull { item ->
                itemOuterCross(item, axis, item.result)
            } ?: 0f
            if (axis.isRow) {
                val baselineItems = line.items.filter { item ->
                    resolvedItemAlignment(item.box.style.alignSelf, box.style.alignItems) ==
                        UiAlignItems.BASELINE
                }
                if (baselineItems.isNotEmpty()) {
                    val ascents = baselineItems.map { item ->
                        val topMargin = item.metrics.margin.top ?: 0f
                        topMargin + (item.result.fragment.baselineFromTop
                            ?: item.result.fragment.borderBox.height)
                    }
                    val baseline = ascents.maxOrNull() ?: 0f
                    val descent = baselineItems.maxOfOrNull { item ->
                        val outer = itemOuterCross(item, axis, item.result)
                        val ascent = (item.metrics.margin.top ?: 0f) +
                            (item.result.fragment.baselineFromTop
                                ?: item.result.fragment.borderBox.height)
                        outer - ascent
                    } ?: 0f
                    line.baseline = baseline
                    line.crossSize = max(line.crossSize, baseline + descent)
                }
            }
        }

        val definiteCross = if (axis.isRow) specifiedContentHeight else contentWidth
        if (lines.size == 1 && definiteCross != null) {
            lines.single().crossSize = max(lines.single().crossSize, definiteCross)
        }
        val naturalCross = lines.sumOf { it.crossSize.toDouble() }.toFloat() +
            crossGap * (lines.size - 1).coerceAtLeast(0)
        val usedCross = definiteCross ?: naturalCross
        positionFlexLines(
            lines = lines,
            usedCross = usedCross,
            naturalCross = naturalCross,
            crossGap = crossGap,
            alignContent = box.style.alignContent,
            wrapReverse = box.style.flexWrap == UiFlexWrap.WRAP_REVERSE,
        )

        // Stretch is resolved after flex line cross sizes are known.
        lines.forEach { line ->
            line.items.forEach { item ->
                val alignment = resolvedItemAlignment(item.box.style.alignSelf, box.style.alignItems)
                val crossAuto = if (axis.isRow) {
                    item.box.style.height == UiSizeValue.AUTO
                } else {
                    item.box.style.width == UiSizeValue.AUTO
                }
                val crossAutoMargins = crossAutoMarginCount(item.metrics.margin, axis)
                if (alignment == UiAlignItems.STRETCH && crossAuto && crossAutoMargins == 0) {
                    val paddingCross = if (axis.isRow) {
                        item.metrics.padding.vertical
                    } else {
                        item.metrics.padding.horizontal
                    }
                    val marginCross = fixedCrossMargins(item.metrics.margin, axis)
                    val forcedCross = (line.crossSize - paddingCross - marginCross).coerceAtLeast(0f)
                    item.result = layoutFlexItem(
                        item = item,
                        axis = axis,
                        targetMain = item.targetMainSize,
                        forcedCross = forcedCross,
                        containerWidth = contentWidth,
                        containerHeight = specifiedContentHeight,
                    )
                }
            }
        }

        val fragments = mutableListOf<CssFragment>()
        val textFragments = mutableListOf<CssTextFragment>()
        lines.forEach { line ->
            positionFlexItemsInLine(
                owner = box,
                line = line,
                axis = axis,
                contentLeft = contentLeft,
                contentTop = contentTop,
                usedMain = usedMain,
                mainGap = mainGap,
            ).forEach(fragments::add)
        }
        return ContentLayout(
            naturalWidth = if (axis.isRow) usedMain else naturalCross,
            naturalHeight = if (axis.isRow) naturalCross else usedMain,
            children = fragments,
            textFragments = textFragments,
            pendingAbsolute = absoluteChildren.map { child ->
                PendingAbsolute(child, contentLeft, contentTop)
            },
            baselineFromTop = if (axis.isRow) {
                fragments.firstOrNull()?.let { fragment ->
                    fragment.borderBox.top - contentTop +
                        (fragment.baselineFromTop ?: fragment.borderBox.height)
                }
            } else {
                fragments.firstOrNull()?.let { fragment ->
                    fragment.borderBox.top - contentTop +
                        (fragment.baselineFromTop ?: fragment.borderBox.height)
                }
            },
        )
    }

    private fun createFlexItemPlan(
        box: CssBox,
        axis: FlexAxis,
        containerWidth: Float,
        containerHeight: Float?,
        availableMain: Float?,
        containerAlignItems: UiAlignItems,
        sourceIndex: Int,
    ): FlexItemPlan {
        val metrics = resolveMetrics(box.style, containerWidth)
        val intrinsic = intrinsicWidths(box)
        val provisional = layoutBox(
            box = box,
            constraints = ConstraintSpace(
                availableWidth = if (axis.isRow) {
                    AvailableSize.MaxContent
                } else {
                    AvailableSize.Definite(containerWidth)
                },
                availableHeight = AvailableSize.Indefinite,
                percentageWidth = containerWidth,
                percentageHeight = containerHeight,
                forcedContentWidth = if (
                    !axis.isRow && resolvedItemAlignment(box.style.alignSelf, containerAlignItems) ==
                    UiAlignItems.STRETCH && box.style.width == UiSizeValue.AUTO
                ) {
                    (containerWidth - metrics.padding.horizontal -
                        fixedCrossMargins(metrics.margin, axis)).coerceAtLeast(0f)
                } else {
                    null
                },
                isFlexItem = true,
            ),
            staticMarginLeft = 0f,
            staticMarginTop = 0f,
        )
        val naturalMain = if (axis.isRow) intrinsic.max else provisional.fragment.contentBox.height
        val intrinsicMain = if (axis.isRow) {
            intrinsic
        } else {
            IntrinsicWidths(provisional.fragment.contentBox.height, provisional.fragment.contentBox.height)
        }
        val mainPadding = if (axis.isRow) metrics.padding.horizontal else metrics.padding.vertical
        val mainProperty = if (axis.isRow) box.style.width else box.style.height
        val percentageBase = availableMain
        val propertyMain = resolvePreferredSize(
            value = mainProperty,
            percentageBase = percentageBase,
            intrinsic = intrinsicMain,
            available = availableMain,
            padding = mainPadding,
            boxSizing = box.style.boxSizing,
        )
        val flexBase = when (val basis = box.style.flexBasis) {
            UiFlexBasis.Auto -> propertyMain ?: naturalMain
            UiFlexBasis.Content -> naturalMain
            UiFlexBasis.MinContent -> intrinsicMain.min
            UiFlexBasis.MaxContent -> intrinsicMain.max
            is UiLength -> basis.resolve(percentageBase)?.let { specified ->
                when (box.style.boxSizing) {
                    UiBoxSizing.CONTENT_BOX -> specified
                    UiBoxSizing.BORDER_BOX -> (specified - mainPadding).coerceAtLeast(0f)
                }
            } ?: naturalMain
        }
        val minProperty = if (axis.isRow) box.style.minWidth else box.style.minHeight
        val maxProperty = if (axis.isRow) box.style.maxWidth else box.style.maxHeight
        val automaticMinimum = propertyMain?.let { specified ->
            min(intrinsicMain.min, specified)
        } ?: intrinsicMain.min
        val minimum = if (minProperty == UiSizeValue.AUTO) {
            automaticMinimum
        } else {
            resolvePreferredSize(
                minProperty,
                percentageBase,
                intrinsicMain,
                availableMain,
                mainPadding,
                box.style.boxSizing,
            ) ?: 0f
        }
        val maximum = resolvePreferredSize(
            maxProperty,
            percentageBase,
            intrinsicMain,
            availableMain,
            mainPadding,
            box.style.boxSizing,
        ) ?: Float.POSITIVE_INFINITY
        return FlexItemPlan(
            box = box,
            metrics = metrics,
            baseMainSize = flexBase.coerceAtLeast(0f),
            minMainSize = minimum.coerceAtMost(maximum),
            maxMainSize = maximum,
            sourceIndex = sourceIndex,
        )
    }

    private fun collectFlexLines(
        plans: List<FlexItemPlan>,
        availableMain: Float?,
        mainGap: Float,
        wrap: Boolean,
        axis: FlexAxis,
    ): MutableList<FlexLine> {
        if (!wrap || availableMain == null) return mutableListOf(FlexLine(plans))
        val lines = mutableListOf<FlexLine>()
        var current = mutableListOf<FlexItemPlan>()
        var occupied = 0f
        plans.forEach { item ->
            val itemSize = itemOuterMain(item, axis, item.targetMainSize)
            val added = itemSize + if (current.isEmpty()) 0f else mainGap
            if (current.isNotEmpty() && occupied + added > availableMain) {
                lines += FlexLine(current)
                current = mutableListOf()
                occupied = 0f
            }
            if (current.isNotEmpty()) occupied += mainGap
            current += item
            occupied += itemSize
        }
        if (current.isNotEmpty()) lines += FlexLine(current)
        return lines
    }

    private fun resolveFlexibleLengths(
        line: FlexLine,
        availableMain: Float,
        mainGap: Float,
        axis: FlexAxis,
    ) {
        val items = line.items
        val gapTotal = mainGap * (items.size - 1).coerceAtLeast(0)
        val hypotheticalOuter = items.sumOf { item ->
            itemOuterMain(item, axis, item.hypotheticalMainSize).toDouble()
        }.toFloat() + gapTotal
        val growing = hypotheticalOuter < availableMain
        items.forEach { it.targetMainSize = it.baseMainSize }
        val unfrozen = items.toMutableSet()

        fun factor(item: FlexItemPlan): Float = if (growing) {
            item.box.style.flexGrow
        } else {
            item.box.style.flexShrink
        }

        fun freeze(item: FlexItemPlan) {
            item.targetMainSize = item.hypotheticalMainSize
            unfrozen -= item
        }

        items.filter { item ->
            factor(item) == 0f ||
                growing && item.baseMainSize > item.hypotheticalMainSize ||
                !growing && item.baseMainSize < item.hypotheticalMainSize
        }.forEach(::freeze)

        fun remainingFreeSpace(): Float {
            val occupied = items.sumOf { item ->
                val size = if (item in unfrozen) item.baseMainSize else item.targetMainSize
                itemOuterMain(item, axis, size).toDouble()
            }.toFloat() + gapTotal
            return availableMain - occupied
        }

        val initialFree = remainingFreeSpace()
        repeat(items.size + 1) {
            if (unfrozen.isEmpty()) return
            var free = remainingFreeSpace()
            val rawFactorSum = unfrozen.sumOf { item -> factor(item).toDouble() }.toFloat()
            if (rawFactorSum < 1f) {
                val partialFree = initialFree * rawFactorSum
                if (abs(partialFree) < abs(free)) free = partialFree
            }

            if (growing) {
                if (rawFactorSum > 0f) {
                    unfrozen.forEach { item ->
                        item.targetMainSize = item.baseMainSize +
                            free * item.box.style.flexGrow / rawFactorSum
                    }
                }
            } else {
                val scaledFactorSum = unfrozen.sumOf { item ->
                    (item.box.style.flexShrink * item.baseMainSize).toDouble()
                }.toFloat()
                unfrozen.forEach { item ->
                    val scaledFactor = item.box.style.flexShrink * item.baseMainSize
                    item.targetMainSize = if (scaledFactorSum > 0f) {
                        item.baseMainSize - abs(free) * scaledFactor / scaledFactorSum
                    } else {
                        item.baseMainSize
                    }
                }
            }

            val violations = unfrozen.associateWith { item ->
                val unclamped = item.targetMainSize
                val clamped = unclamped.coerceIn(item.minMainSize, item.maxMainSize)
                item.targetMainSize = clamped
                clamped - unclamped
            }
            val totalViolation = violations.values.sum()
            val toFreeze = when {
                totalViolation > 0f -> violations.filterValues { it > 0f }.keys
                totalViolation < 0f -> violations.filterValues { it < 0f }.keys
                else -> unfrozen.toSet()
            }
            if (toFreeze.isEmpty()) {
                unfrozen.clear()
            } else {
                unfrozen.removeAll(toFreeze)
            }
        }
    }

    private fun layoutFlexItem(
        item: FlexItemPlan,
        axis: FlexAxis,
        targetMain: Float,
        forcedCross: Float?,
        containerWidth: Float,
        containerHeight: Float?,
    ): BoxLayoutResult = layoutBox(
        box = item.box,
        constraints = ConstraintSpace(
            availableWidth = AvailableSize.Definite(containerWidth),
            availableHeight = containerHeight?.let(AvailableSize::Definite)
                ?: AvailableSize.Indefinite,
            percentageWidth = containerWidth,
            percentageHeight = containerHeight,
            forcedContentWidth = if (axis.isRow) targetMain else forcedCross,
            forcedContentHeight = if (axis.isRow) forcedCross else targetMain,
            isFlexItem = true,
        ),
        staticMarginLeft = 0f,
        staticMarginTop = 0f,
    )

    private fun itemOuterMain(item: FlexItemPlan, axis: FlexAxis, contentMain: Float): Float {
        val padding = if (axis.isRow) item.metrics.padding.horizontal else item.metrics.padding.vertical
        val margin = fixedMainMargins(item.metrics.margin, axis)
        return (contentMain + padding + margin).coerceAtLeast(0f)
    }

    private fun itemOuterCross(
        item: FlexItemPlan,
        axis: FlexAxis,
        result: BoxLayoutResult,
    ): Float = if (axis.isRow) {
        result.fragment.borderBox.height + fixedCrossMargins(item.metrics.margin, axis)
    } else {
        result.fragment.borderBox.width + fixedCrossMargins(item.metrics.margin, axis)
    }

    private fun fixedMainMargins(margin: ResolvedMargins, axis: FlexAxis): Float = if (axis.isRow) {
        (margin.left ?: 0f) + (margin.right ?: 0f)
    } else {
        (margin.top ?: 0f) + (margin.bottom ?: 0f)
    }

    private fun fixedCrossMargins(margin: ResolvedMargins, axis: FlexAxis): Float = if (axis.isRow) {
        (margin.top ?: 0f) + (margin.bottom ?: 0f)
    } else {
        (margin.left ?: 0f) + (margin.right ?: 0f)
    }

    private fun crossAutoMarginCount(margin: ResolvedMargins, axis: FlexAxis): Int = if (axis.isRow) {
        listOf(margin.top, margin.bottom).count { it == null }
    } else {
        listOf(margin.left, margin.right).count { it == null }
    }

    private fun mainAutoMarginCount(margin: ResolvedMargins, axis: FlexAxis): Int = if (axis.isRow) {
        listOf(margin.left, margin.right).count { it == null }
    } else {
        listOf(margin.top, margin.bottom).count { it == null }
    }

    private fun positionFlexLines(
        lines: List<FlexLine>,
        usedCross: Float,
        naturalCross: Float,
        crossGap: Float,
        alignContent: UiAlignContent,
        wrapReverse: Boolean,
    ) {
        if (lines.isEmpty()) return
        val resolvedAlignment = when (alignContent) {
            UiAlignContent.NORMAL -> UiAlignContent.STRETCH
            else -> alignContent
        }
        var free = usedCross - naturalCross
        if (resolvedAlignment == UiAlignContent.STRETCH && free > 0f) {
            val addition = free / lines.size
            lines.forEach { it.crossSize += addition }
            free = 0f
        }
        val (start, distributedGap) = contentDistribution(
            alignment = resolvedAlignment,
            freeSpace = free,
            itemCount = lines.size,
        )
        var cursor = start
        lines.forEach { line ->
            line.crossOffset = if (wrapReverse) {
                usedCross - cursor - line.crossSize
            } else {
                cursor
            }
            cursor += line.crossSize + crossGap + distributedGap
        }
    }

    private fun contentDistribution(
        alignment: UiAlignContent,
        freeSpace: Float,
        itemCount: Int,
    ): Pair<Float, Float> {
        val positive = freeSpace.coerceAtLeast(0f)
        return when (alignment) {
            UiAlignContent.NORMAL,
            UiAlignContent.STRETCH,
            UiAlignContent.START,
            UiAlignContent.FLEX_START,
            -> 0f to 0f

            UiAlignContent.END,
            UiAlignContent.FLEX_END,
            -> freeSpace to 0f

            UiAlignContent.CENTER -> freeSpace / 2f to 0f
            UiAlignContent.SPACE_BETWEEN ->
                0f to if (itemCount > 1) positive / (itemCount - 1) else 0f

            UiAlignContent.SPACE_AROUND -> {
                val gap = if (itemCount > 0) positive / itemCount else 0f
                gap / 2f to gap
            }

            UiAlignContent.SPACE_EVENLY -> {
                val gap = positive / (itemCount + 1)
                gap to gap
            }
        }
    }

    private fun positionFlexItemsInLine(
        owner: CssBox,
        line: FlexLine,
        axis: FlexAxis,
        contentLeft: Float,
        contentTop: Float,
        usedMain: Float,
        mainGap: Float,
    ): List<CssFragment> {
        val items = line.items
        val fixedOccupied = items.sumOf { item ->
            itemOuterMain(item, axis, item.targetMainSize).toDouble()
        }.toFloat() + mainGap * (items.size - 1).coerceAtLeast(0)
        var freeMain = usedMain - fixedOccupied
        val autoMarginCount = items.sumOf { mainAutoMarginCount(it.metrics.margin, axis) }
        val autoMainMargin = if (autoMarginCount > 0 && freeMain > 0f) {
            val share = freeMain / autoMarginCount
            freeMain = 0f
            share
        } else {
            0f
        }
        items.forEach { item ->
            val base = item.metrics.margin.withAutoAsZero()
            item.usedMargin = if (axis.isRow) {
                base.copy(
                    left = if (item.metrics.margin.left == null) autoMainMargin else base.left,
                    right = if (item.metrics.margin.right == null) autoMainMargin else base.right,
                )
            } else {
                base.copy(
                    top = if (item.metrics.margin.top == null) autoMainMargin else base.top,
                    bottom = if (item.metrics.margin.bottom == null) autoMainMargin else base.bottom,
                )
            }
        }
        val (mainStart, distributedGap) = justifyDistribution(
            owner.style.justifyContent,
            freeMain,
            items.size,
        )
        var cursor = if (axis.isMainReverse) usedMain - mainStart else mainStart
        return items.map { item ->
            var usedMargin = item.usedMargin
            val borderCross = if (axis.isRow) {
                item.result.fragment.borderBox.height
            } else {
                item.result.fragment.borderBox.width
            }
            val fixedCross = if (axis.isRow) {
                usedMargin.top + usedMargin.bottom
            } else {
                usedMargin.left + usedMargin.right
            }
            val crossFree = line.crossSize - borderCross - fixedCross
            val crossAutos = crossAutoMarginCount(item.metrics.margin, axis)
            val crossAutoShare = if (crossAutos > 0 && crossFree > 0f) crossFree / crossAutos else 0f
            if (axis.isRow) {
                usedMargin = usedMargin.copy(
                    top = if (item.metrics.margin.top == null) crossAutoShare else usedMargin.top,
                    bottom = if (item.metrics.margin.bottom == null) crossAutoShare else usedMargin.bottom,
                )
            } else {
                usedMargin = usedMargin.copy(
                    left = if (item.metrics.margin.left == null) crossAutoShare else usedMargin.left,
                    right = if (item.metrics.margin.right == null) crossAutoShare else usedMargin.right,
                )
            }
            val actualCrossOuter = borderCross + if (axis.isRow) {
                usedMargin.top + usedMargin.bottom
            } else {
                usedMargin.left + usedMargin.right
            }
            val alignment = resolvedItemAlignment(item.box.style.alignSelf, owner.style.alignItems)
            val crossOffset = if (crossAutos > 0) {
                0f
            } else if (alignment == UiAlignItems.BASELINE && axis.isRow && line.baseline != null) {
                line.baseline!! - usedMargin.top -
                    (item.result.fragment.baselineFromTop
                        ?: item.result.fragment.borderBox.height)
            } else {
                crossAlignmentOffset(alignment, line.crossSize - actualCrossOuter)
            }
            val outerMain = if (axis.isRow) {
                item.result.fragment.borderBox.width + usedMargin.left + usedMargin.right
            } else {
                item.result.fragment.borderBox.height + usedMargin.top + usedMargin.bottom
            }
            val mainPosition = if (axis.isMainReverse) {
                cursor - outerMain
            } else {
                cursor
            }
            val marginLeft = if (axis.isRow) {
                contentLeft + mainPosition
            } else {
                contentLeft + line.crossOffset + crossOffset
            }
            val marginTop = if (axis.isRow) {
                contentTop + line.crossOffset + crossOffset
            } else {
                contentTop + mainPosition
            }
            val positioned = positionFragment(
                item.result.fragment,
                marginLeft,
                marginTop,
                usedMargin,
            )
            val step = outerMain + mainGap + distributedGap
            cursor += if (axis.isMainReverse) -step else step
            positioned
        }
    }

    private fun justifyDistribution(
        alignment: UiJustifyContent,
        freeSpace: Float,
        itemCount: Int,
    ): Pair<Float, Float> {
        val positive = freeSpace.coerceAtLeast(0f)
        return when (alignment) {
            UiJustifyContent.NORMAL,
            UiJustifyContent.START,
            UiJustifyContent.FLEX_START,
            -> 0f to 0f

            UiJustifyContent.END,
            UiJustifyContent.FLEX_END,
            -> freeSpace to 0f

            UiJustifyContent.CENTER -> freeSpace / 2f to 0f
            UiJustifyContent.SPACE_BETWEEN ->
                0f to if (itemCount > 1) positive / (itemCount - 1) else 0f

            UiJustifyContent.SPACE_AROUND -> {
                val gap = if (itemCount > 0) positive / itemCount else 0f
                gap / 2f to gap
            }

            UiJustifyContent.SPACE_EVENLY -> {
                val gap = positive / (itemCount + 1)
                gap to gap
            }
        }
    }

    private fun resolvedItemAlignment(
        self: UiAlignSelf,
        items: UiAlignItems,
    ): UiAlignItems = when (self) {
        UiAlignSelf.AUTO -> when (items) {
            UiAlignItems.NORMAL -> UiAlignItems.STRETCH
            else -> items
        }

        UiAlignSelf.NORMAL, UiAlignSelf.STRETCH -> UiAlignItems.STRETCH
        UiAlignSelf.START -> UiAlignItems.START
        UiAlignSelf.END -> UiAlignItems.END
        UiAlignSelf.FLEX_START -> UiAlignItems.FLEX_START
        UiAlignSelf.FLEX_END -> UiAlignItems.FLEX_END
        UiAlignSelf.CENTER -> UiAlignItems.CENTER
        UiAlignSelf.BASELINE -> UiAlignItems.BASELINE
    }

    private fun crossAlignmentOffset(alignment: UiAlignItems, freeSpace: Float): Float =
        when (alignment) {
            UiAlignItems.END, UiAlignItems.FLEX_END -> freeSpace
            UiAlignItems.CENTER -> freeSpace / 2f
            UiAlignItems.NORMAL,
            UiAlignItems.STRETCH,
            UiAlignItems.START,
            UiAlignItems.FLEX_START,
            UiAlignItems.BASELINE,
            -> 0f
        }

    private fun positionFragment(
        fragment: CssFragment,
        staticMarginLeft: Float,
        staticMarginTop: Float,
        usedMargin: UsedEdges,
    ): CssFragment {
        val relativeX = fragment.borderBox.left - fragment.usedMargin.left
        val relativeY = fragment.borderBox.top - fragment.usedMargin.top
        val visualMarginLeft = staticMarginLeft + relativeX
        val visualMarginTop = staticMarginTop + relativeY
        val targetBorderLeft = visualMarginLeft + usedMargin.left
        val targetBorderTop = visualMarginTop + usedMargin.top
        val moved = fragment.translated(
            targetBorderLeft - fragment.borderBox.left,
            targetBorderTop - fragment.borderBox.top,
        )
        return moved.copy(
            marginBox = safeMarginRect(
                visualMarginLeft,
                visualMarginTop,
                moved.borderBox.width + usedMargin.horizontal,
                moved.borderBox.height + usedMargin.vertical,
            ),
            usedMargin = usedMargin,
        )
    }

    private fun relativeOffset(
        style: ResolvedUiStyle,
        constraints: ConstraintSpace,
    ): Pair<Float, Float> {
        val left = resolveInset(style.left, constraints.percentageWidth)
        val right = resolveInset(style.right, constraints.percentageWidth)
        val top = resolveInset(style.top, constraints.percentageHeight)
        val bottom = resolveInset(style.bottom, constraints.percentageHeight)
        return (left ?: right?.let { -it } ?: 0f) to
            (top ?: bottom?.let { -it } ?: 0f)
    }

    private fun resolveInset(value: UiInsetValue, percentageBase: Float?): Float? = when (value) {
        UiInsetValue.Auto -> null
        is UiLength -> value.resolve(percentageBase)
    }

    private fun resolveAbsoluteDescendants(
        fragment: CssFragment,
        inheritedContainingBlock: UiRect,
    ): CssFragment {
        val containingBlock = if (fragment.box.style.position != UiPosition.STATIC) {
            fragment.paddingBox
        } else {
            inheritedContainingBlock
        }
        val normalChildren = fragment.children.map { child ->
            resolveAbsoluteDescendants(child, containingBlock)
        }
        val absoluteChildren = fragment.pendingAbsolute.map { pending ->
            val absolute = layoutAbsolute(pending, containingBlock)
            resolveAbsoluteDescendants(absolute, containingBlock)
        }
        return fragment.copy(
            children = normalChildren + absoluteChildren,
            pendingAbsolute = emptyList(),
        )
    }

    private fun layoutAbsolute(
        pending: PendingAbsolute,
        containingBlock: UiRect,
    ): CssFragment {
        val box = pending.box
        val style = box.style
        val metrics = resolveMetrics(style, containingBlock.width)
        val left = resolveInset(style.left, containingBlock.width)
        val right = resolveInset(style.right, containingBlock.width)
        val top = resolveInset(style.top, containingBlock.height)
        val bottom = resolveInset(style.bottom, containingBlock.height)
        val widthIsAuto = style.width == UiSizeValue.AUTO
        val heightIsAuto = style.height == UiSizeValue.AUTO
        val forcedWidth = if (widthIsAuto && left != null && right != null) {
            (containingBlock.width - left - right - metrics.padding.horizontal -
                fixedHorizontalMargins(metrics.margin)).coerceAtLeast(0f)
        } else {
            null
        }
        val forcedHeight = if (heightIsAuto && top != null && bottom != null) {
            (containingBlock.height - top - bottom - metrics.padding.vertical -
                fixedVerticalMargins(metrics.margin)).coerceAtLeast(0f)
        } else {
            null
        }
        val availableWidth = (containingBlock.width - (left ?: 0f) - (right ?: 0f))
            .coerceAtLeast(0f)
        val result = layoutBox(
            box = box,
            constraints = ConstraintSpace(
                availableWidth = AvailableSize.Definite(availableWidth),
                availableHeight = AvailableSize.Definite(containingBlock.height),
                percentageWidth = containingBlock.width,
                percentageHeight = containingBlock.height,
                forcedContentWidth = forcedWidth,
                forcedContentHeight = forcedHeight,
                shrinkToFit = true,
            ),
            staticMarginLeft = 0f,
            staticMarginTop = 0f,
        )
        var usedMargin = result.fragment.usedMargin
        if (left != null && right != null) {
            val remaining = containingBlock.width - left - right -
                result.fragment.borderBox.width - fixedHorizontalMargins(metrics.margin)
            val autoCount = listOf(metrics.margin.left, metrics.margin.right).count { it == null }
            if (autoCount > 0 && remaining >= 0f) {
                val share = remaining / autoCount
                usedMargin = usedMargin.copy(
                    left = if (metrics.margin.left == null) share else usedMargin.left,
                    right = if (metrics.margin.right == null) share else usedMargin.right,
                )
            }
        }
        if (top != null && bottom != null) {
            val remaining = containingBlock.height - top - bottom -
                result.fragment.borderBox.height - fixedVerticalMargins(metrics.margin)
            val autoCount = listOf(metrics.margin.top, metrics.margin.bottom).count { it == null }
            if (autoCount > 0 && remaining >= 0f) {
                val share = remaining / autoCount
                usedMargin = usedMargin.copy(
                    top = if (metrics.margin.top == null) share else usedMargin.top,
                    bottom = if (metrics.margin.bottom == null) share else usedMargin.bottom,
                )
            }
        }
        val outerWidth = result.fragment.borderBox.width + usedMargin.horizontal
        val outerHeight = result.fragment.borderBox.height + usedMargin.vertical
        val marginLeft = when {
            left != null -> containingBlock.left + left
            right != null -> containingBlock.right - right - outerWidth
            else -> pending.staticLeft
        }
        val marginTop = when {
            top != null -> containingBlock.top + top
            bottom != null -> containingBlock.bottom - bottom - outerHeight
            else -> pending.staticTop
        }
        return positionFragment(result.fragment, marginLeft, marginTop, usedMargin)
    }

    private fun fixedHorizontalMargins(margin: ResolvedMargins): Float =
        (margin.left ?: 0f) + (margin.right ?: 0f)

    private fun fixedVerticalMargins(margin: ResolvedMargins): Float =
        (margin.top ?: 0f) + (margin.bottom ?: 0f)
}

private fun CssFragment.translated(deltaX: Float, deltaY: Float): CssFragment {
    if (deltaX == 0f && deltaY == 0f) return this
    return copy(
        marginBox = marginBox.translated(deltaX, deltaY),
        borderBox = borderBox.translated(deltaX, deltaY),
        paddingBox = paddingBox.translated(deltaX, deltaY),
        contentBox = contentBox.translated(deltaX, deltaY),
        children = children.map { it.translated(deltaX, deltaY) },
        textFragments = textFragments.map { fragment ->
            fragment.copy(bounds = fragment.bounds.translated(deltaX, deltaY))
        },
        pendingAbsolute = pendingAbsolute.map { pending ->
            pending.copy(
                staticLeft = pending.staticLeft + deltaX,
                staticTop = pending.staticTop + deltaY,
            )
        },
    )
}

private fun UiRect.translated(deltaX: Float, deltaY: Float): UiRect = copy(
    left = left + deltaX,
    top = top + deltaY,
)
