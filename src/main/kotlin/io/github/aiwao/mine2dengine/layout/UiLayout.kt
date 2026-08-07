package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DFont
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.roundToInt

/** The calculated geometry for one UI element. */
data class UiLayoutNode(
    val element: UiElement,
    /** Includes the element's margin and begins at its layout coordinate. */
    val outerBounds: UiRect,
    /** The rectangle painted by background-color. */
    val bounds: UiRect,
    /** The area available to text or children after padding. */
    val contentBounds: UiRect,
    val children: List<UiLayoutNode>,
    /** The font resolved from this element's style and its ancestors. */
    val font: Mine2DFont? = null,
    /** The text color resolved from this element's style and its ancestors. */
    val color: Int = UiStyle.DEFAULT_COLOR,
    /** The text shadow setting resolved from this element's style and its ancestors. */
    val dropShadow: Boolean = UiStyle.DEFAULT_DROP_SHADOW,
)

/** A layout result that can render and dispatch pointer input to UI elements. */
class UiLayout internal constructor(
    root: UiLayoutNode,
) {
    var root: UiLayoutNode = root
        private set

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

    val size: UiSize = UiSize(root.outerBounds.width, root.outerBounds.height)

    internal fun moveTo(left: Float, top: Float) {
        require(left.isFinite()) { "Left must be finite: $left" }
        require(top.isFinite()) { "Top must be finite: $top" }

        val deltaX = left - this.left
        val deltaY = top - this.top
        if (deltaX == 0f && deltaY == 0f) return

        root = root.translated(deltaX, deltaY)
    }

    /** Renders this layout without recalculating its geometry. */
    fun render(renderer: Mine2DEngine) {
        draw(root, renderer, ResolvedUiTextStyle())
    }

    /** Moves this layout to [left], [top], then renders it without recalculating its size. */
    fun render(renderer: Mine2DEngine, left: Float, top: Float) {
        moveTo(left, top)
        render(renderer)
    }

    /** Finds the deepest element at the given GUI coordinate. */
    fun elementAt(x: Float, y: Float): UiElement? =
        nodesInPaintOrder().asReversed().firstOrNull { it.bounds.contains(x, y) }?.element

    /**
     * Invokes the topmost clickable element at the GUI coordinate in [event].
     * Elements with an [UiElement.onClick] or [UiElement.onDrag] callback are clickable. A
     * [Button] remains clickable without a callback, preserving its control semantics. The hit
     * element starts dragging until [release] is called. Returns true when one was hit.
     */
    fun click(event: MouseButtonEvent): Boolean {
        val x = event.x().toFloat()
        val y = event.y().toFloat()
        val nodes = nodesInPaintOrder()
        val element = nodes
            .asReversed()
            .firstOrNull { node ->
                (node.element is Button ||
                    node.element.onClick != null ||
                    node.element.onDrag != null) &&
                    node.bounds.contains(x, y)
            }
            ?.element
            ?: return false

        nodes.forEach { node -> node.element.dragging = false }
        element.dragging = true
        element.onClick?.invoke(event)
        return true
    }

    /**
     * Updates [UiElement.hovering] and invokes mouse-over or mouse-out callbacks, invokes the
     * topmost [UiElement.onMouseMove] callback at [x], [y], then invokes [UiElement.onDrag] on the
     * dragging element even when the pointer is outside its bounds. Returns true when at least
     * one callback was invoked.
     */
    fun mouseMove(x: Double, y: Double): Boolean {
        val layoutX = x.toFloat()
        val layoutY = y.toFloat()
        val nodes = nodesInPaintOrder()
        var handled = false

        nodes
            .asReversed()
            .filter { node -> node.element.hovering && !node.bounds.contains(layoutX, layoutY) }
            .forEach { node ->
                val onMouseOut = node.element.onMouseOut
                onMouseOut?.invoke()
                handled = handled || onMouseOut != null
                node.element.hovering = false
            }

        nodes
            .filter { node -> !node.element.hovering && node.bounds.contains(layoutX, layoutY) }
            .forEach { node ->
                val onMouseOver = node.element.onMouseOver
                onMouseOver?.invoke()
                handled = handled || onMouseOver != null
                node.element.hovering = true
            }

        nodes
            .asReversed()
            .firstOrNull { node ->
                node.element.onMouseMove != null &&
                    node.bounds.contains(layoutX, layoutY)
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
                onDrag(x, y)
                handled = true
            }

        return handled
    }

    /** Stops the current drag. Returns true when an element was dragging. */
    fun release(): Boolean {
        val draggingElements = nodesInPaintOrder()
            .map(UiLayoutNode::element)
            .filter(UiElement::dragging)
            .distinct()
        if (draggingElements.isEmpty()) return false

        draggingElements.forEach { element -> element.dragging = false }
        return true
    }

    fun nodeOf(element: UiElement): UiLayoutNode? =
        nodesInPaintOrder().firstOrNull { it.element === element }

    internal fun nodesInPaintOrder(): List<UiLayoutNode> = buildList {
        fun addTree(node: UiLayoutNode) {
            add(node)
            node.children.forEach(::addTree)
        }
        addTree(root)
    }

    private fun draw(
        node: UiLayoutNode,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
    ) {
        val style = node.element.style
        val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
        style.backgroundColor?.let { color ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.quad(
                    node.bounds.left,
                    node.bounds.top,
                    node.bounds.width,
                    node.bounds.height,
                    color,
                )
            }
        }

        when (val element = node.element) {
            is UiContainer -> Unit
            is Paragraph -> drawText(
                element.text,
                style,
                resolvedTextStyle,
                node.contentBounds,
                requireFont(node),
                renderer,
            )
        }

        node.children.forEach { child -> draw(child, renderer, resolvedTextStyle) }
    }

    private fun drawText(
        text: String,
        style: UiStyle,
        resolvedTextStyle: ResolvedUiTextStyle,
        contentBounds: UiRect,
        font: Mine2DFont,
        renderer: Mine2DEngine,
    ) {
        val textMeasurer = Mine2DTextMeasurer(font)
        val lines = textLines(text)
        val textTop = contentBounds.top + alignedTop(
            availableHeight = contentBounds.height,
            itemHeight = lines.size * textMeasurer.lineHeight,
            alignment = style.verticalAlignment,
        )
        lines.forEachIndexed { index, line ->
            val lineWidth = textMeasurer.width(line)
            val x = alignedLeft(
                availableWidth = contentBounds.width,
                itemWidth = lineWidth,
                alignment = style.horizontalAlignment,
            ) + contentBounds.left
            val y = textTop + index * textMeasurer.lineHeight
            renderer.text(
                font,
                line,
                x.roundToInt(),
                y.roundToInt(),
                resolvedTextStyle.color,
                dropShadow = resolvedTextStyle.dropShadow,
            )
        }
    }

    private fun requireFont(node: UiLayoutNode): Mine2DFont =
        requireNotNull(node.font) {
            "${node.element.javaClass.simpleName} requires a font in its style or an ancestor style"
        }
}

private fun UiLayoutNode.translated(deltaX: Float, deltaY: Float): UiLayoutNode = copy(
    outerBounds = outerBounds.translated(deltaX, deltaY),
    bounds = bounds.translated(deltaX, deltaY),
    contentBounds = contentBounds.translated(deltaX, deltaY),
    children = children.map { child -> child.translated(deltaX, deltaY) },
)

private fun UiRect.translated(deltaX: Float, deltaY: Float): UiRect = copy(
    left = left + deltaX,
    top = top + deltaY,
)
