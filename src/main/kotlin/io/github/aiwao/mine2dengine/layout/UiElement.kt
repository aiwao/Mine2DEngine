package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent

/** Base type for nodes in a UI tree. */
sealed class UiElement(
    style: UiStyle,
    open var onClick: ((MouseButtonEvent) -> Unit)? = null,
    open var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    open var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    open var onMouseOver: (() -> Unit)? = null,
    open var onMouseOut: (() -> Unit)? = null,
) {
    private var styleProvider: () -> UiStyle = { style }

    /**
     * The element's current style.
     *
     * Assigning a value replaces a dynamic style supplied when this element was created.
     */
    var style: UiStyle
        get() = styleProvider()
        set(value) {
            styleProvider = { value }
        }

    internal fun setStyleProvider(provider: () -> UiStyle) {
        styleProvider = provider
    }

    /** Whether this element's click callback is disabled. */
    var disabled: Boolean = false

    /** Whether this element has been clicked and not yet released. */
    var dragging: Boolean = false
        internal set

    /** Whether the pointer is currently inside this element's bounds. */
    var hovering: Boolean = false
        internal set
}

/** Base type for UI elements that arrange child elements. */
sealed class UiContainer(
    style: UiStyle,
    children: Iterable<UiElement> = emptyList(),
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
) : UiElement(style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut) {
    val children: MutableList<UiElement> = children.toMutableList()

    fun <T : UiElement> add(element: T): T {
        children += element
        return element
    }

    fun div(
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: Div.() -> Unit = {},
    ): Div = add(
        Div(
            style = style,
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
        ).apply(content),
    )

    /** Creates a div whose style is resolved from its current state when used. */
    fun div(
        style: (Div) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: Div.() -> Unit = {},
    ): Div = add(
        Div(
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
        ).withStyleProvider(style).apply(content),
    )

    fun p(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
    ): Paragraph = add(
        Paragraph(text, style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut),
    )

    /** Creates a paragraph whose style is resolved from its current state when used. */
    fun p(
        text: String,
        style: (Paragraph) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
    ): Paragraph = add(
        Paragraph(
            text,
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
        ).withStyleProvider(style),
    )

    fun paragraph(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
    ): Paragraph = p(text, style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut)

    /** Alias of [p] with a dynamic style. */
    fun paragraph(
        text: String,
        style: (Paragraph) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
    ): Paragraph = p(text, style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut)

    fun button(
        style: UiStyle = Button.DEFAULT_STYLE,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: Button.() -> Unit = {},
    ): Button = add(
        Button(
            style = style,
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
        ).apply(content),
    )

    /** Creates a button whose style is resolved from its current state when used. */
    fun button(
        style: (Button) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: Button.() -> Unit = {},
    ): Button = add(
        Button(
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
        ).withStyleProvider(style).apply(content),
    )
}

/** A container corresponding to an HTML div. */
class Div(
    style: UiStyle = UiStyle(),
    children: Iterable<UiElement> = emptyList(),
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
) : UiContainer(style, children, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut)

/** A text element corresponding to an HTML p. Newlines create multiple lines. */
class Paragraph(
    var text: String,
    style: UiStyle = UiStyle(),
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
) : UiElement(style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut)

/** A clickable container. Invoke it through [UiLayout.mouseClick] after rendering or layout. */
class Button(
    style: UiStyle = DEFAULT_STYLE,
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    children: Iterable<UiElement> = emptyList(),
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
) : UiContainer(style, children, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut) {
    companion object {
        /** A visible default which can be replaced with any [UiStyle]. */
        val DEFAULT_STYLE = UiStyle(
            background = UiPaint(0xFF555555.toInt()),
            padding = UiEdges(vertical = 3f, horizontal = 6f),
        )
    }
}

/** Creates the root of a UI tree. */
fun div(
    style: UiStyle = UiStyle(),
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    content: Div.() -> Unit = {},
): Div = Div(
    style = style,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
).apply(content)

/** Creates the root of a UI tree with a style resolved from the div's current state. */
fun div(
    style: (Div) -> UiStyle,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    content: Div.() -> Unit = {},
): Div = Div(
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
).withStyleProvider(style).apply(content)

private fun <T : UiElement> T.withStyleProvider(provider: (T) -> UiStyle): T = apply {
    setStyleProvider { provider(this) }
}
