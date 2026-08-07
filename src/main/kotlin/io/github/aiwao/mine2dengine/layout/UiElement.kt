package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent

/** Base type for nodes in a UI tree. */
sealed class UiElement(
    open var style: UiStyle,
    open var onClick: ((MouseButtonEvent) -> Unit)? = null,
)

/** Base type for UI elements that arrange child elements. */
sealed class UiContainer(
    override var style: UiStyle,
    children: Iterable<UiElement> = emptyList(),
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
) : UiElement(style, onClick) {
    val children: MutableList<UiElement> = children.toMutableList()

    fun <T : UiElement> add(element: T): T {
        children += element
        return element
    }

    fun div(
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        content: Div.() -> Unit = {},
    ): Div = add(Div(style, onClick = onClick).apply(content))

    fun p(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
    ): Paragraph = add(Paragraph(text, style, onClick))

    fun paragraph(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
    ): Paragraph = p(text, style, onClick)

    fun button(
        style: UiStyle = Button.DEFAULT_STYLE,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        content: Button.() -> Unit = {},
    ): Button = add(Button(style, onClick).apply(content))
}

/** A container corresponding to an HTML div. */
class Div(
    style: UiStyle = UiStyle(),
    children: Iterable<UiElement> = emptyList(),
    onClick: ((MouseButtonEvent) -> Unit)? = null,
) : UiContainer(style, children, onClick)

/** A text element corresponding to an HTML p. Newlines create multiple lines. */
class Paragraph(
    var text: String,
    override var style: UiStyle = UiStyle(),
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
) : UiElement(style, onClick)

/** A clickable container. Invoke it through [UiLayout.click] after rendering or layout. */
class Button(
    style: UiStyle = DEFAULT_STYLE,
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    children: Iterable<UiElement> = emptyList(),
) : UiContainer(style, children, onClick) {
    companion object {
        /** A visible default which can be replaced with any [UiStyle]. */
        val DEFAULT_STYLE = UiStyle(
            backgroundColor = 0xFF555555.toInt(),
            padding = UiEdges(vertical = 3f, horizontal = 6f),
        )
    }
}

/** Creates the root of a UI tree. */
fun div(
    style: UiStyle = UiStyle(),
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    content: Div.() -> Unit = {},
): Div = Div(style, onClick = onClick).apply(content)
