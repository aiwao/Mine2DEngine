package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DFont
import net.minecraft.client.input.MouseButtonEvent

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
)

/** A layout result that can also dispatch clicks to UI elements. */
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

    /** Finds the deepest element at the given GUI coordinate. */
    fun elementAt(x: Float, y: Float): UiElement? =
        nodesInPaintOrder().asReversed().firstOrNull { it.bounds.contains(x, y) }?.element

    /**
     * Invokes the topmost clickable element at the GUI coordinate in [event].
     * Elements with an [UiElement.onClick] callback are clickable. A [Button] remains clickable
     * without a callback, preserving its control semantics. Returns true when one was hit.
     */
    fun click(event: MouseButtonEvent): Boolean {
        val x = event.x().toFloat()
        val y = event.y().toFloat()
        val element = nodesInPaintOrder()
            .asReversed()
            .firstOrNull { node ->
                (node.element is Button || node.element.onClick != null) &&
                    node.bounds.contains(x, y)
            }
            ?.element
            ?: return false

        element.onClick?.invoke(event)
        return true
    }

    /**
     * Invokes the topmost element with an [UiElement.onMouseMove] callback at [x], [y].
     * Returns true when a matching element was hit.
     */
    fun mouseMove(x: Double, y: Double): Boolean {
        val layoutX = x.toFloat()
        val layoutY = y.toFloat()
        val element = nodesInPaintOrder()
            .asReversed()
            .firstOrNull { node ->
                node.element.onMouseMove != null &&
                    node.bounds.contains(layoutX, layoutY)
            }
            ?.element
            ?: return false

        element.onMouseMove?.invoke(x, y)
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
