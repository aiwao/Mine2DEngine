package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent

/** Base type for nodes in a UI tree. */
sealed class UiElement(
    tag: String,
    className: Set<String>,
    id: String,
    style: UiStyle,
    open var onClick: ((MouseButtonEvent) -> Unit)? = null,
    open var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    open var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    open var onMouseOver: (() -> Unit)? = null,
    open var onMouseOut: (() -> Unit)? = null,
) {
    /** The tag name for this element. Names are matched exactly and may contain whitespace. */
    var tag: String = tag
        private set

    /** The HTML-compatible ID for this element, or an empty string when it has no ID. */
    var id: String = id
        private set

    /** The class names for this element. Names are matched exactly and may contain whitespace. */
    val className: Set<String> = className.toSet()

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

    internal fun addStyleOverrides(provider: () -> UiStyle) {
        val baseStyleProvider = styleProvider
        styleProvider = { baseStyleProvider().withOverrides(provider()) }
    }

    /** Null outside a component root; an empty list still marks a component style boundary. */
    internal var componentStyleSheets: List<StyleSheet>? = null

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
    tag: String,
    className: Set<String>,
    id: String,
    style: UiStyle,
    children: Iterable<UiElement> = emptyList(),
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
    styleSheets: Iterable<StyleSheet> = emptyList(),
) : UiElement(tag, className, id, style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut) {
    val children: MutableList<UiElement> = children.toMutableList()

    /**
     * Style sheets scoped to this container and its descendants.
     *
     * [TargetScope] selects this container. An outer container's sheets can style a nested
     * component root but do not enter that component's descendants. Mutating this list after
     * layout requires recalculating the layout.
     */
    val styleSheets: MutableList<StyleSheet> = styleSheets.toMutableList()

    fun <T : UiElement> add(element: T): T {
        children += element
        return element
    }

    /**
     * Creates and adds one instance of [component] with call-site style and event overrides.
     *
     * Specified [style] values override the component root's style. Null callbacks preserve the
     * callbacks created by the component factory. [content] is built once by the component factory
     * at the position it chooses in the created tree.
     */
    fun <T : UiElement> component(
        component: UiComponent<T>,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: UiContent = {},
    ): T = mountComponent(
        component = component,
        resolveStyle = { style },
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        content = content,
    )

    /**
     * Creates an instance of [component] with style resolved from its root's current state.
     *
     * Specified style values override the component root's style. Null callbacks preserve the
     * callbacks created by the component factory. [content] is built once by the component factory
     * at the position it chooses in the created tree.
     */
    fun <T : UiElement> component(
        component: UiComponent<T>,
        style: (T) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: UiContent = {},
    ): T = mountComponent(
        component = component,
        resolveStyle = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        content = content,
    )

    private fun <T : UiElement> mountComponent(
        component: UiComponent<T>,
        resolveStyle: (T) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)?,
        onMouseMove: ((x: Double, y: Double) -> Unit)?,
        onDrag: ((MouseButtonEvent) -> Unit)?,
        onMouseOver: (() -> Unit)?,
        onMouseOut: (() -> Unit)?,
        content: UiContent,
    ): T {
        var contentBuilt = false
        val singleUseContent: UiContent = {
            check(!contentBuilt) { "Component content may only be built once" }
            contentBuilt = true
            content(this)
        }
        return component.create(singleUseContent).also { root ->
            root.componentStyleSheets = component.styleSheets.toList()
            root.addStyleOverrides { resolveStyle(root) }
            onClick?.let { root.onClick = it }
            onMouseMove?.let { root.onMouseMove = it }
            onDrag?.let { root.onDrag = it }
            onMouseOver?.let { root.onMouseOver = it }
            onMouseOut?.let { root.onMouseOut = it }
            add(root)
        }
    }

    fun div(
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        styleSheets: Iterable<StyleSheet> = emptyList(),
        tag: String = "div",
        className: Set<String> = emptySet(),
        id: String = "",
        content: Div.() -> Unit = {},
    ): Div = add(
        Div(
            style = style,
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
            styleSheets = styleSheets,
            tag = tag,
            className = className,
            id = id,
        ).apply(content),
    )

    /** Creates a div with [className] as its single, unsplit class name. */
    fun div(
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        styleSheets: Iterable<StyleSheet> = emptyList(),
        tag: String = "div",
        className: String,
        id: String = "",
        content: Div.() -> Unit = {},
    ): Div = div(
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        styleSheets = styleSheets,
        tag = tag,
        className = setOf(className),
        id = id,
        content = content,
    )

    /** Creates a div whose style is resolved from its current state when used. */
    fun div(
        style: (Div) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        styleSheets: Iterable<StyleSheet> = emptyList(),
        tag: String = "div",
        className: Set<String> = emptySet(),
        id: String = "",
        content: Div.() -> Unit = {},
    ): Div = add(
        Div(
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
            styleSheets = styleSheets,
            tag = tag,
            className = className,
            id = id,
        ).withStyleProvider(style).apply(content),
    )

    /** Creates a dynamically styled div with [className] as its single, unsplit class name. */
    fun div(
        style: (Div) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        styleSheets: Iterable<StyleSheet> = emptyList(),
        tag: String = "div",
        className: String,
        id: String = "",
        content: Div.() -> Unit = {},
    ): Div = div(
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        styleSheets = styleSheets,
        tag = tag,
        className = setOf(className),
        id = id,
        content = content,
    )

    fun p(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: Set<String> = emptySet(),
        id: String = "",
    ): Paragraph = add(
        Paragraph(
            text,
            style,
            onClick,
            onMouseMove,
            onDrag,
            onMouseOver,
            onMouseOut,
            tag,
            className,
            id,
        ),
    )

    /** Creates a paragraph with [className] as its single, unsplit class name. */
    fun p(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: String,
        id: String = "",
    ): Paragraph = p(
        text = text,
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = setOf(className),
        id = id,
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
        tag: String = "p",
        className: Set<String> = emptySet(),
        id: String = "",
    ): Paragraph = add(
        Paragraph(
            text,
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
            tag = tag,
            className = className,
            id = id,
        ).withStyleProvider(style),
    )

    /** Creates a dynamically styled paragraph with [className] as its single class name. */
    fun p(
        text: String,
        style: (Paragraph) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: String,
        id: String = "",
    ): Paragraph = p(
        text = text,
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = setOf(className),
        id = id,
    )

    fun paragraph(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: Set<String> = emptySet(),
        id: String = "",
    ): Paragraph = p(
        text,
        style,
        onClick,
        onMouseMove,
        onDrag,
        onMouseOver,
        onMouseOut,
        tag,
        className,
        id,
    )

    /** Alias of [p] with [className] as its single, unsplit class name. */
    fun paragraph(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: String,
        id: String = "",
    ): Paragraph = p(
        text = text,
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = setOf(className),
        id = id,
    )

    /** Alias of [p] with a dynamic style. */
    fun paragraph(
        text: String,
        style: (Paragraph) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: Set<String> = emptySet(),
        id: String = "",
    ): Paragraph = p(
        text,
        style,
        onClick,
        onMouseMove,
        onDrag,
        onMouseOver,
        onMouseOut,
        tag,
        className,
        id,
    )

    /** Dynamic-style alias of [p] with [className] as its single, unsplit class name. */
    fun paragraph(
        text: String,
        style: (Paragraph) -> UiStyle,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: String,
        id: String = "",
    ): Paragraph = p(
        text = text,
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = setOf(className),
        id = id,
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
    styleSheets: Iterable<StyleSheet> = emptyList(),
    tag: String = "div",
    className: Set<String> = emptySet(),
    id: String = "",
) : UiContainer(
    tag,
    className,
    id,
    style,
    children,
    onClick,
    onMouseMove,
    onDrag,
    onMouseOver,
    onMouseOut,
    styleSheets,
) {
    /** Creates a div with [className] as its single, unsplit class name. */
    constructor(
        style: UiStyle = UiStyle(),
        children: Iterable<UiElement> = emptyList(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        styleSheets: Iterable<StyleSheet> = emptyList(),
        tag: String = "div",
        className: String,
        id: String = "",
    ) : this(
        style = style,
        children = children,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        styleSheets = styleSheets,
        tag = tag,
        className = setOf(className),
        id = id,
    )
}

/** A text element corresponding to an HTML p. Newlines create multiple lines. */
class Paragraph(
    var text: String,
    style: UiStyle = UiStyle(),
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
    tag: String = "p",
    className: Set<String> = emptySet(),
    id: String = "",
) : UiElement(tag, className, id, style, onClick, onMouseMove, onDrag, onMouseOver, onMouseOut) {
    /** Creates a paragraph with [className] as its single, unsplit class name. */
    constructor(
        text: String,
        style: UiStyle = UiStyle(),
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: String,
        id: String = "",
    ) : this(
        text = text,
        style = style,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = setOf(className),
        id = id,
    )
}

/** Creates the root of a UI tree. */
fun div(
    style: UiStyle = UiStyle(),
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    styleSheets: Iterable<StyleSheet> = emptyList(),
    tag: String = "div",
    className: Set<String> = emptySet(),
    id: String = "",
    content: Div.() -> Unit = {},
): Div = Div(
    style = style,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    styleSheets = styleSheets,
    tag = tag,
    className = className,
    id = id,
).apply(content)

/** Creates the root of a UI tree with [className] as its single, unsplit class name. */
fun div(
    style: UiStyle = UiStyle(),
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    styleSheets: Iterable<StyleSheet> = emptyList(),
    tag: String = "div",
    className: String,
    id: String = "",
    content: Div.() -> Unit = {},
): Div = div(
    style = style,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    styleSheets = styleSheets,
    tag = tag,
    className = setOf(className),
    id = id,
    content = content,
)

/** Creates the root of a UI tree with a style resolved from the div's current state. */
fun div(
    style: (Div) -> UiStyle,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    styleSheets: Iterable<StyleSheet> = emptyList(),
    tag: String = "div",
    className: Set<String> = emptySet(),
    id: String = "",
    content: Div.() -> Unit = {},
): Div = Div(
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    styleSheets = styleSheets,
    tag = tag,
    className = className,
    id = id,
).withStyleProvider(style).apply(content)

/** Creates a dynamically styled root with [className] as its single, unsplit class name. */
fun div(
    style: (Div) -> UiStyle,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    styleSheets: Iterable<StyleSheet> = emptyList(),
    tag: String = "div",
    className: String,
    id: String = "",
    content: Div.() -> Unit = {},
): Div = div(
    style = style,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    styleSheets = styleSheets,
    tag = tag,
    className = setOf(className),
    id = id,
    content = content,
)

private fun <T : UiElement> T.withStyleProvider(provider: (T) -> UiStyle): T = apply {
    setStyleProvider { provider(this) }
}
