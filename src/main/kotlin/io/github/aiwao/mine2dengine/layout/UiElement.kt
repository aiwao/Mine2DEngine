package io.github.aiwao.mine2dengine.layout

/** Base type for nodes in a UI tree. */
sealed class UiElement(
    open var style: UiStyle,
)

/** A container corresponding to an HTML div. */
class Div(
    override var style: UiStyle = UiStyle(),
    children: Iterable<UiElement> = emptyList(),
) : UiElement(style) {
    val children: MutableList<UiElement> = children.toMutableList()

    fun <T : UiElement> add(element: T): T {
        children += element
        return element
    }

    fun div(
        style: UiStyle = UiStyle(),
        content: Div.() -> Unit = {},
    ): Div = add(Div(style).apply(content))

    fun p(text: String, style: UiStyle = UiStyle()): Paragraph =
        add(Paragraph(text, style))

    fun paragraph(text: String, style: UiStyle = UiStyle()): Paragraph =
        p(text, style)

    fun button(
        text: String,
        style: UiStyle = Button.DEFAULT_STYLE,
        onClick: (() -> Unit)? = null,
    ): Button = add(Button(text, style, onClick))
}

/** A text element corresponding to an HTML p. Newlines create multiple lines. */
class Paragraph(
    var text: String,
    override var style: UiStyle = UiStyle(),
) : UiElement(style)

/** A text button. Invoke it through [UiLayout.click] after rendering or layout. */
class Button(
    var text: String,
    override var style: UiStyle = DEFAULT_STYLE,
    var onClick: (() -> Unit)? = null,
) : UiElement(style) {
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
    content: Div.() -> Unit = {},
): Div = Div(style).apply(content)
