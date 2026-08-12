package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DFont
import io.github.aiwao.mine2dengine.Mine2DMaterial
import io.github.aiwao.mine2dengine.Mine2DUniformRect
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import java.util.IdentityHashMap

internal data class UiDisplayKey(
    val element: UiElement,
    val pseudoElement: UiPseudoElement? = null,
)

internal data class UiDisplayState(
    val key: UiDisplayKey,
    val predicate: () -> Boolean,
    val suppressed: Boolean,
)

internal data class UiLayoutSnapshot(
    val root: UiLayoutNode,
    val rootFragment: UiBoxFragment,
    val displayStates: List<UiDisplayState>,
)

/** One generated CSS box fragment in the final layout tree. */
data class UiBoxFragment(
    val element: UiElement,
    val pseudoElement: UiPseudoElement? = null,
    val marginBox: UiRect,
    val borderBox: UiRect,
    val paddingBox: UiRect,
    val contentBox: UiRect,
    val children: List<UiBoxFragment>,
    /** False for an anonymous box or a root retained only as a `display: none` handle. */
    val generatesBox: Boolean = true,
)

/** One line fragment produced while laying out text. */
data class UiTextLayoutFragment(
    val text: String,
    val bounds: UiRect,
)

internal data class UiStyledTextLayoutFragment(
    val fragment: UiTextLayoutFragment,
    /** Null means that the element's current inherited text style should be used at paint time. */
    val textStyle: ResolvedUiTextStyle?,
)

/** The calculated geometry for one UI element. */
data class UiLayoutNode(
    val element: UiElement,
    /** Includes the element's margin and begins at its layout coordinate. */
    val outerBounds: UiRect,
    /** The rectangle painted when the element has a background color. */
    val bounds: UiRect,
    /** The area available to text or children after padding. */
    val contentBounds: UiRect,
    val children: List<UiLayoutNode>,
    /** The font resolved from this element's style and its ancestors. */
    val font: Mine2DFont? = null,
    /** The text color resolved from this element's style and its ancestors. */
    val color: Int = UiStyle.DEFAULT_COLOR,
    /** The configurable text shadow resolved from this element's style and its ancestors. */
    val textShadow: UiTextShadow? = null,
    /** Whether this node generated a layout box. */
    internal val displayed: Boolean = true,
    internal val beforePseudo: UiPseudoLayoutNode? = null,
    internal val afterPseudo: UiPseudoLayoutNode? = null,
    internal val textBounds: UiRect? = null,
    internal val textFragments: List<UiTextLayoutFragment> = emptyList(),
) {
    internal var styleProvider: () -> ResolvedUiStyle = { element.style.resolveDefaults() }
    internal var styledTextFragments: List<UiStyledTextLayoutFragment> = emptyList()
}

/** Calculated geometry and content for one generated pseudo-element box. */
data class UiPseudoLayoutNode(
    /** The author-created element which generated this box. */
    val element: UiElement,
    val pseudoElement: UiPseudoElement,
    /** Includes the generated box's margin. */
    val outerBounds: UiRect,
    /** The rectangle painted by the generated box's background. */
    val bounds: UiRect,
    /** The generated text area after padding. */
    val contentBounds: UiRect,
    /** The content captured by the layout pass. */
    val content: UiGeneratedContent,
    /** The font resolved from the pseudo-element style and its originating element. */
    val font: Mine2DFont?,
    val displayed: Boolean = true,
    internal val textFragments: List<UiTextLayoutFragment> = emptyList(),
) {
    internal var pseudoStyleProvider: () -> UiPseudoStyle = { UiPseudoStyle(content) }
}

/** A layout result that can render and dispatch pointer input to UI elements. */
class UiLayout internal constructor(
    snapshot: UiLayoutSnapshot,
    private val relayout: (Float, Float, Map<UiDisplayKey, Boolean>) -> UiLayoutSnapshot,
) {
    var root: UiLayoutNode = snapshot.root
        private set

    /** The CSS fragment tree, including pseudo-elements and anonymous layout boxes. */
    var rootFragment: UiBoxFragment = snapshot.rootFragment
        private set

    private var displayStates: List<UiDisplayState> = snapshot.displayStates

    private var dragButtonInfo: MouseButtonInfo? = null

    /** The left coordinate of the root's outer box. Changing it translates the complete layout. */
    var left: Float
        get() = root.outerBounds.left
        set(value) {
            moveTo(value, top)
        }

    /** The top coordinate of the root's outer box. Changing it translates the complete layout. */
    var top: Float
        get() = root.outerBounds.top
        set(value) {
            moveTo(left, value)
        }

    val size: UiSize
        get() {
            refreshDisplay()
            return UiSize(root.outerBounds.width, root.outerBounds.height)
        }

    internal fun moveTo(left: Float, top: Float) {
        require(left.isFinite()) { "Left must be finite: $left" }
        require(top.isFinite()) { "Top must be finite: $top" }

        val deltaX = left - this.left
        val deltaY = top - this.top
        if (deltaX == 0f && deltaY == 0f) return

        root = root.translated(deltaX, deltaY)
        rootFragment = rootFragment.translated(deltaX, deltaY)
    }

    /** Renders this layout, recalculating geometry when a none-display value changes. */
    fun render(renderer: Mine2DEngine) {
        refreshDisplay()
        draw(root, renderer, ResolvedUiTextStyle(), renderer.uniformTimeSeconds())
    }

    /** Moves this layout to [left], [top], then renders it without recalculating its size. */
    fun render(renderer: Mine2DEngine, left: Float, top: Float) {
        moveTo(left, top)
        render(renderer)
    }

    /** Finds the deepest element at the given GUI coordinate. */
    fun elementAt(x: Float, y: Float): UiElement? {
        refreshDisplay()
        return hitRegionsInPaintOrder()
            .asReversed()
            .firstOrNull { region -> region.bounds.contains(x, y) }
            ?.element
    }

    /**
     * Invokes the topmost clickable element at the GUI coordinate in [event].
     * Elements with an [UiElement.onClick] or [UiElement.onDrag] callback are clickable. The hit
     * element starts dragging until [mouseRelease] is called. Its [UiElement.onClick] callback is
     * not invoked while [UiElement.disabled] is true. Returns true when one was hit.
     */
    fun mouseClick(event: MouseButtonEvent): Boolean {
        refreshDisplay()
        val x = event.x().toFloat()
        val y = event.y().toFloat()
        val nodes = nodesInPaintOrder()
        val element = hitRegionsInPaintOrder()
            .asReversed()
            .firstOrNull { region ->
                (region.element.onClick != null ||
                    region.element.onDrag != null) &&
                    region.bounds.contains(x, y)
            }
            ?.element
            ?: return false

        nodes.forEach { node -> node.element.dragging = false }
        element.dragging = true
        dragButtonInfo = event.buttonInfo()
        if (!element.disabled) {
            element.onClick?.invoke(event)
        }
        return true
    }

    /**
     * Updates [UiElement.hovering] and invokes mouse-over or mouse-out callbacks, invokes the
     * topmost [UiElement.onMouseMove] callback at [x], [y], then invokes [UiElement.onDrag] on the
     * dragging element with a [MouseButtonEvent] containing the current coordinates and the
     * button information from [mouseClick], even when the pointer is outside its bounds. Returns
     * true when at least one callback was invoked.
     */
    fun mouseMove(x: Double, y: Double): Boolean {
        refreshDisplay()
        val layoutX = x.toFloat()
        val layoutY = y.toFloat()
        val nodes = nodesInPaintOrder()
        val hitRegions = hitRegionsInPaintOrder()
        fun contains(element: UiElement): Boolean = hitRegions.any { region ->
            region.element === element && region.bounds.contains(layoutX, layoutY)
        }
        var handled = false

        nodes
            .asReversed()
            .filter { node -> node.element.hovering && !contains(node.element) }
            .forEach { node ->
                val onMouseOut = node.element.onMouseOut
                onMouseOut?.invoke()
                handled = handled || onMouseOut != null
                node.element.hovering = false
            }

        nodes
            .filter { node -> !node.element.hovering && contains(node.element) }
            .forEach { node ->
                val onMouseOver = node.element.onMouseOver
                onMouseOver?.invoke()
                handled = handled || onMouseOver != null
                node.element.hovering = true
            }

        hitRegions
            .asReversed()
            .firstOrNull { region ->
                region.element.onMouseMove != null &&
                    region.bounds.contains(layoutX, layoutY)
            }
            ?.element
            ?.onMouseMove
            ?.let { onMouseMove ->
                onMouseMove(x, y)
                handled = true
            }

        nodes
            .asReversed()
            .firstOrNull { node -> node.element.dragging && node.element.onDrag != null }
            ?.element
            ?.onDrag
            ?.let { onDrag ->
                val buttonInfo = checkNotNull(dragButtonInfo) {
                    "A dragging element must have mouse button information"
                }
                onDrag(MouseButtonEvent(x, y, buttonInfo))
                handled = true
            }

        return handled
    }

    /** Stops the current drag. Returns true when an element was dragging. */
    fun mouseRelease(): Boolean {
        refreshDisplay()
        val draggingElements = nodesInPaintOrder()
            .map(UiLayoutNode::element)
            .filter(UiElement::dragging)
            .distinct()
        if (draggingElements.isEmpty()) {
            dragButtonInfo = null
            return false
        }

        draggingElements.forEach { element -> element.dragging = false }
        dragButtonInfo = null
        return true
    }

    fun nodeOf(element: UiElement): UiLayoutNode? {
        refreshDisplay()
        return nodesInPaintOrder().firstOrNull { it.element === element }
    }

    /** Returns every generated CSS box fragment associated with [element]. */
    fun fragmentsOf(element: UiElement): List<UiBoxFragment> {
        refreshDisplay()
        return buildList {
            fun visit(fragment: UiBoxFragment) {
                if (fragment.generatesBox && fragment.element === element) add(fragment)
                fragment.children.forEach(::visit)
            }
            visit(rootFragment)
        }
    }

    /**
     * Returns the generated pseudo-element layout box owned by [element].
     *
     * Returns null when no rule matches or the generated box has `display: none`.
     */
    fun pseudoNodeOf(
        element: UiElement,
        pseudoElement: UiPseudoElement,
    ): UiPseudoLayoutNode? {
        val node = nodeOf(element) ?: return null
        return when (pseudoElement) {
            UiPseudoElement.BEFORE -> node.beforePseudo
            UiPseudoElement.AFTER -> node.afterPseudo
        }
    }

    internal fun nodesInPaintOrder(): List<UiLayoutNode> = buildList {
        fun addTree(node: UiLayoutNode) {
            add(node)
            node.children.forEach(::addTree)
        }
        addTree(root)
    }

    private data class UiHitRegion(
        val element: UiElement,
        val bounds: UiRect,
    )

    private sealed interface UiPaintContent {
        val order: Int
        val sourceIndex: Int

        data class Pseudo(
            val node: UiPseudoLayoutNode,
            override val order: Int,
            override val sourceIndex: Int,
        ) : UiPaintContent

        data object Text : UiPaintContent {
            override val order: Int = 0
            override val sourceIndex: Int = 0
        }

        data class Child(
            val node: UiLayoutNode,
            override val order: Int,
            override val sourceIndex: Int,
        ) : UiPaintContent
    }

    private fun paintContents(
        node: UiLayoutNode,
        style: ResolvedUiStyle = node.styleProvider(),
    ): List<UiPaintContent> {
        val contents = buildList {
            node.beforePseudo?.let { pseudo ->
                add(
                    UiPaintContent.Pseudo(
                        node = pseudo,
                        order = pseudo.pseudoStyleProvider().style.resolveDefaults().order,
                        sourceIndex = -1,
                    ),
                )
            }
            if (node.element is Paragraph || node.textFragments.isNotEmpty()) {
                add(UiPaintContent.Text)
            }
            node.children.forEachIndexed { index, child ->
                add(
                    UiPaintContent.Child(
                        node = child,
                        order = child.styleProvider().order,
                        sourceIndex = index,
                    ),
                )
            }
            node.afterPseudo?.let { pseudo ->
                add(
                    UiPaintContent.Pseudo(
                        node = pseudo,
                        order = pseudo.pseudoStyleProvider().style.resolveDefaults().order,
                        sourceIndex = Int.MAX_VALUE,
                    ),
                )
            }
        }
        return if (style.display.box?.inside == UiDisplayInside.FLEX) {
            contents.sortedWith(
                compareBy<UiPaintContent>(UiPaintContent::order)
                    .thenBy(UiPaintContent::sourceIndex),
            )
        } else {
            contents
        }
    }

    private fun hitRegionsInPaintOrder(): List<UiHitRegion> = buildList {
        fun addTree(node: UiLayoutNode) {
            add(UiHitRegion(node.element, node.bounds))
            paintContents(node).forEach { content ->
                when (content) {
                    is UiPaintContent.Pseudo -> content.node
                        .takeIf(UiPseudoLayoutNode::displayed)
                        ?.let { pseudo -> add(UiHitRegion(pseudo.element, pseudo.bounds)) }

                    UiPaintContent.Text -> Unit
                    is UiPaintContent.Child -> addTree(content.node)
                }
            }
        }
        addTree(root)
    }

    private fun refreshDisplay() {
        val evaluatedDisplays = mutableMapOf<UiDisplayKey, Boolean>()
        var changed = false
        displayStates.forEach { state ->
            val suppressed = state.predicate()
            evaluatedDisplays[state.key] = suppressed
            changed = changed || suppressed != state.suppressed
        }
        if (!changed) return

        val previousNodes = nodesInPaintOrder()
        val snapshot = relayout(left, top, evaluatedDisplays)
        root = snapshot.root
        rootFragment = snapshot.rootFragment
        displayStates = snapshot.displayStates

        val displayedElements = java.util.Collections.newSetFromMap(
            IdentityHashMap<UiElement, Boolean>(),
        )
        nodesInPaintOrder()
            .filter(UiLayoutNode::displayed)
            .mapTo(displayedElements, UiLayoutNode::element)
        val hiddenElements = previousNodes
            .map(UiLayoutNode::element)
            .filterNot(displayedElements::contains)
        if (hiddenElements.any(UiElement::dragging)) {
            dragButtonInfo = null
        }
        hiddenElements.forEach { element ->
            element.dragging = false
            element.hovering = false
        }
    }

    private fun draw(
        node: UiLayoutNode,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
    ) {
        if (!node.displayed) return

        val style = node.styleProvider()
        val dropShadow = style.dropShadow
        if (dropShadow != null) {
            renderer.withDropShadow(
                x = node.bounds.left,
                y = node.bounds.top,
                width = node.bounds.width,
                height = node.bounds.height,
                color = dropShadow.color,
                offsetX = dropShadow.offsetX,
                offsetY = dropShadow.offsetY,
                blurRadius = dropShadow.blurRadius,
            ) {
                drawContents(node, style, renderer, inheritedTextStyle, timeSeconds)
            }
        } else {
            drawContents(node, style, renderer, inheritedTextStyle, timeSeconds)
        }
    }

    private fun drawContents(
        node: UiLayoutNode,
        style: ResolvedUiStyle,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
    ) {
        val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
        style.boxShadow?.let { shadow ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.boxShadow(
                    x = node.bounds.left,
                    y = node.bounds.top,
                    width = node.bounds.width,
                    height = node.bounds.height,
                    color = shadow.color,
                    offsetX = shadow.offsetX,
                    offsetY = shadow.offsetY,
                    blurRadius = shadow.blurRadius,
                    spreadRadius = shadow.spreadRadius,
                    cornerRadius = shadow.cornerRadius,
                )
            }
        }
        style.drawBackground(renderer.material) { color, material ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.quad(
                    node.bounds.left,
                    node.bounds.top,
                    node.bounds.width,
                    node.bounds.height,
                    color,
                    material,
                    renderer.uniformContext(
                        elementBounds = node.bounds.toUniformRect(),
                        contentBounds = node.contentBounds.toUniformRect(),
                        timeSeconds = timeSeconds,
                    ),
                )
            }
        }

        paintContents(node, style).forEach { content ->
            when (content) {
                is UiPaintContent.Pseudo ->
                    drawPseudo(content.node, renderer, resolvedTextStyle, timeSeconds)

                UiPaintContent.Text -> {
                    if (node.textFragments.isNotEmpty()) {
                        drawStyledTextFragments(
                            node = node,
                            fallbackTextStyle = resolvedTextStyle,
                            renderer = renderer,
                        )
                    } else if (node.element is Paragraph && node.element.text.isNotEmpty()) {
                        drawText(
                            node.element.text,
                            style,
                            resolvedTextStyle,
                            node.textBounds ?: node.contentBounds,
                            requireFont(node),
                            renderer,
                        )
                    }
                }

                is UiPaintContent.Child ->
                    draw(content.node, renderer, resolvedTextStyle, timeSeconds)
            }
        }
    }

    private fun drawPseudo(
        node: UiPseudoLayoutNode,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
    ) {
        if (!node.displayed) return

        val pseudoStyle = node.pseudoStyleProvider()
        val style = pseudoStyle.style.resolveDefaults()
        val dropShadow = style.dropShadow
        if (dropShadow != null) {
            renderer.withDropShadow(
                x = node.bounds.left,
                y = node.bounds.top,
                width = node.bounds.width,
                height = node.bounds.height,
                color = dropShadow.color,
                offsetX = dropShadow.offsetX,
                offsetY = dropShadow.offsetY,
                blurRadius = dropShadow.blurRadius,
            ) {
                drawPseudoContents(
                    node,
                    pseudoStyle.content,
                    style,
                    renderer,
                    inheritedTextStyle,
                    timeSeconds,
                )
            }
        } else {
            drawPseudoContents(
                node,
                pseudoStyle.content,
                style,
                renderer,
                inheritedTextStyle,
                timeSeconds,
            )
        }
    }

    private fun drawPseudoContents(
        node: UiPseudoLayoutNode,
        content: UiGeneratedContent,
        style: ResolvedUiStyle,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
    ) {
        val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
        style.boxShadow?.let { shadow ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.boxShadow(
                    x = node.bounds.left,
                    y = node.bounds.top,
                    width = node.bounds.width,
                    height = node.bounds.height,
                    color = shadow.color,
                    offsetX = shadow.offsetX,
                    offsetY = shadow.offsetY,
                    blurRadius = shadow.blurRadius,
                    spreadRadius = shadow.spreadRadius,
                    cornerRadius = shadow.cornerRadius,
                )
            }
        }
        style.drawBackground(renderer.material) { color, material ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.quad(
                    node.bounds.left,
                    node.bounds.top,
                    node.bounds.width,
                    node.bounds.height,
                    color,
                    material,
                    renderer.uniformContext(
                        elementBounds = node.bounds.toUniformRect(),
                        contentBounds = node.contentBounds.toUniformRect(),
                        timeSeconds = timeSeconds,
                    ),
                )
            }
        }
        if (content is UiGeneratedContent.Text) {
            val font = requireNotNull(node.font) {
                "${node.pseudoElement.cssName} on ${node.element.javaClass.simpleName} " +
                    "requires a font in its style or originating element"
            }
            if (node.textFragments.isNotEmpty()) {
                drawTextFragments(node.textFragments, resolvedTextStyle, font, renderer)
            } else {
                drawText(
                    content.value,
                    style,
                    resolvedTextStyle,
                    node.contentBounds,
                    font,
                    renderer,
                )
            }
        }
    }

    private fun drawTextFragments(
        fragments: List<UiTextLayoutFragment>,
        resolvedTextStyle: ResolvedUiTextStyle,
        font: Mine2DFont,
        renderer: Mine2DEngine,
    ) {
        fragments.forEach { fragment ->
            val y = textRendererY(
                lineBoxTop = fragment.bounds.top,
                lineIndex = 0,
                lineHeight = fragment.bounds.height,
                rendererOffsetFromLineTop = font.rendererOffsetFromLineTop,
            )
            val textOrigin = renderer.pixelAlignedTextOriginY(fragment.bounds.left, y)
            resolvedTextStyle.textShadow?.let { shadow ->
                renderer.textShadow(
                    font = font,
                    text = fragment.text,
                    x = textOrigin.x,
                    y = textOrigin.y,
                    color = shadow.color,
                    offsetX = shadow.offsetX,
                    offsetY = shadow.offsetY,
                    blurRadius = shadow.blurRadius,
                )
            }
            renderer.text(
                font,
                fragment.text,
                textOrigin.x,
                textOrigin.y,
                resolvedTextStyle.color,
            )
        }
    }

    private fun drawStyledTextFragments(
        node: UiLayoutNode,
        fallbackTextStyle: ResolvedUiTextStyle,
        renderer: Mine2DEngine,
    ) {
        node.styledTextFragments.forEach { styled ->
            val textStyle = styled.textStyle ?: fallbackTextStyle
            val font = requireNotNull(textStyle.font) {
                "${node.element.javaClass.simpleName} requires a font in its style or an " +
                    "ancestor style"
            }
            drawTextFragments(listOf(styled.fragment), textStyle, font, renderer)
        }
    }

    private fun drawText(
        text: String,
        style: ResolvedUiStyle,
        resolvedTextStyle: ResolvedUiTextStyle,
        contentBounds: UiRect,
        font: Mine2DFont,
        renderer: Mine2DEngine,
    ) {
        val textMeasurer = Mine2DTextMeasurer(font)
        val lines = textLines(text)
        val textTop = contentBounds.top
        lines.forEachIndexed { index, line ->
            val lineWidth = textMeasurer.width(line)
            val x = contentBounds.left + when (resolvedTextStyle.textAlign) {
                UiTextAlign.START, UiTextAlign.LEFT -> 0f
                UiTextAlign.END, UiTextAlign.RIGHT -> contentBounds.width - lineWidth
                UiTextAlign.CENTER -> (contentBounds.width - lineWidth) / 2f
            }
            val y = textRendererY(
                lineBoxTop = textTop,
                lineIndex = index,
                lineHeight = textMeasurer.lineHeight,
                rendererOffsetFromLineTop = font.rendererOffsetFromLineTop,
            )
            val textOrigin = renderer.pixelAlignedTextOriginY(x, y)
            resolvedTextStyle.textShadow
                ?.let { shadow ->
                    renderer.textShadow(
                        font = font,
                        text = line,
                        x = textOrigin.x,
                        y = textOrigin.y,
                        color = shadow.color,
                        offsetX = shadow.offsetX,
                        offsetY = shadow.offsetY,
                        blurRadius = shadow.blurRadius,
                    )
                }
            renderer.text(
                font,
                line,
                textOrigin.x,
                textOrigin.y,
                resolvedTextStyle.color,
            )
        }
    }

    private fun requireFont(node: UiLayoutNode): Mine2DFont =
        requireNotNull(node.font) {
            "${node.element.javaClass.simpleName} requires a font in its style or an ancestor style"
        }
}

internal fun textRendererY(
    lineBoxTop: Float,
    lineIndex: Int,
    lineHeight: Float,
    rendererOffsetFromLineTop: Float,
): Float = lineBoxTop + rendererOffsetFromLineTop + lineIndex * lineHeight

internal fun UiStyle.drawBackground(
    rendererMaterial: Mine2DMaterial,
    draw: (color: Int, material: Mine2DMaterial) -> Unit,
) {
    backgroundColor?.let { color ->
        draw(color, backgroundMaterial ?: rendererMaterial)
    }
}

internal fun ResolvedUiStyle.drawBackground(
    rendererMaterial: Mine2DMaterial,
    draw: (color: Int, material: Mine2DMaterial) -> Unit,
) {
    backgroundColor?.let { color ->
        draw(color, backgroundMaterial ?: rendererMaterial)
    }
}

private fun UiLayoutNode.translated(deltaX: Float, deltaY: Float): UiLayoutNode = copy(
    outerBounds = outerBounds.translated(deltaX, deltaY),
    bounds = bounds.translated(deltaX, deltaY),
    contentBounds = contentBounds.translated(deltaX, deltaY),
    children = children.map { child -> child.translated(deltaX, deltaY) },
    beforePseudo = beforePseudo?.translated(deltaX, deltaY),
    afterPseudo = afterPseudo?.translated(deltaX, deltaY),
    textBounds = textBounds?.translated(deltaX, deltaY),
    textFragments = textFragments.map { fragment ->
        fragment.copy(bounds = fragment.bounds.translated(deltaX, deltaY))
    },
).also { translated ->
    translated.styleProvider = styleProvider
    translated.styledTextFragments = styledTextFragments.map { styled ->
        styled.copy(
            fragment = styled.fragment.copy(
                bounds = styled.fragment.bounds.translated(deltaX, deltaY),
            ),
        )
    }
}

private fun UiPseudoLayoutNode.translated(
    deltaX: Float,
    deltaY: Float,
): UiPseudoLayoutNode = copy(
    outerBounds = outerBounds.translated(deltaX, deltaY),
    bounds = bounds.translated(deltaX, deltaY),
    contentBounds = contentBounds.translated(deltaX, deltaY),
    textFragments = textFragments.map { fragment ->
        fragment.copy(bounds = fragment.bounds.translated(deltaX, deltaY))
    },
).also { translated ->
    translated.pseudoStyleProvider = pseudoStyleProvider
}

private fun UiRect.translated(deltaX: Float, deltaY: Float): UiRect = copy(
    left = left + deltaX,
    top = top + deltaY,
)

private fun UiBoxFragment.translated(deltaX: Float, deltaY: Float): UiBoxFragment = copy(
    marginBox = marginBox.translated(deltaX, deltaY),
    borderBox = borderBox.translated(deltaX, deltaY),
    paddingBox = paddingBox.translated(deltaX, deltaY),
    contentBox = contentBox.translated(deltaX, deltaY),
    children = children.map { child -> child.translated(deltaX, deltaY) },
)

private fun UiRect.toUniformRect(): Mine2DUniformRect = Mine2DUniformRect(
    left = left,
    top = top,
    width = width,
    height = height,
)
