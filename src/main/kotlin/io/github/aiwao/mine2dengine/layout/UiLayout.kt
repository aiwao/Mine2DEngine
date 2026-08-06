package io.github.aiwao.mine2dengine.layout

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
)

/** A layout result that can also dispatch clicks to buttons. */
class UiLayout internal constructor(
    val root: UiLayoutNode,
) {
    val size: UiSize = UiSize(root.outerBounds.width, root.outerBounds.height)

    /** Finds the deepest element at the given GUI coordinate. */
    fun elementAt(x: Float, y: Float): UiElement? =
        nodesInPaintOrder().asReversed().firstOrNull { it.bounds.contains(x, y) }?.element

    /**
     * Invokes the topmost button at the given GUI coordinate.
     * Returns true when a button was hit, even when it has no callback.
     */
    fun click(x: Float, y: Float): Boolean {
        val button = nodesInPaintOrder()
            .asReversed()
            .firstOrNull { it.element is Button && it.bounds.contains(x, y) }
            ?.element as? Button
            ?: return false

        button.onClick?.invoke()
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
