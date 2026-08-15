package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

/** Base type for nodes in a UI tree. */
sealed class UiElement(
    tag: String,
    className: Set<String>,
    id: String,
    style: UiStyle,
    tabIndex: Int? = null,
    open var onFocus: (() -> Unit)? = null,
    open var onBlur: (() -> Unit)? = null,
    open var onKeyPressed: ((KeyEvent) -> Unit)? = null,
    open var onClick: ((MouseButtonEvent) -> Unit)? = null,
    open var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    open var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    open var onMouseOver: (() -> Unit)? = null,
    open var onMouseOut: (() -> Unit)? = null,
) {
    /**
     * Optional identity used while reconciling children produced by a component.
     *
     * Keys need only be unique among siblings. Unkeyed elements are matched by position.
     */
    var key: Any? = null

    /** The tag name for this element. Names are matched exactly and may contain whitespace. */
    var tag: String = tag
        private set

    /** The HTML-compatible ID for this element, or an empty string when it has no ID. */
    var id: String = id
        private set

    /** The class names for this element. Names are matched exactly and may contain whitespace. */
    val className: Set<String> = className.toSet()

    private var styleProvider: (UiElement) -> UiStyle = { style }

    /**
     * The element's current style.
     *
     * Assigning a value replaces a dynamic style supplied when this element was created.
     */
    var style: UiStyle
        get() = styleProvider(this)
        set(value) {
            styleProvider = { value }
        }

    internal fun setStyleProvider(provider: (UiElement) -> UiStyle) {
        styleProvider = provider
    }

    internal fun copyStyleProviderFrom(element: UiElement) {
        styleProvider = element.styleProvider
    }

    internal fun styleProviderSnapshot(): (UiElement) -> UiStyle = styleProvider

    internal fun restoreStyleProvider(provider: (UiElement) -> UiStyle) {
        styleProvider = provider
    }

    internal fun addStyleOverrides(provider: (UiElement) -> UiStyle) {
        val baseStyleProvider = styleProvider
        styleProvider = { element ->
            baseStyleProvider(element).withOverrides(provider(element))
        }
    }

    /** The component mount whose host root this element represents, if any. */
    internal var componentInstance: ComponentInstance? = null

    /** Null outside a component root; an empty list still marks a component style boundary. */
    internal var componentStyleSheets: List<StyleSheet>? = null

    /** Whether this element rejects keyboard focus, standard interaction, and click callbacks. */
    var disabled: Boolean = false

    /**
     * Keyboard focus participation. Null disables focus, -1 allows pointer/programmatic focus,
     * and non-negative values participate in sequential focus navigation.
     */
    var tabIndex: Int? = tabIndex
        set(value) {
            require(value == null || value >= -1) {
                "tabIndex must be null or at least -1: $value"
            }
            field = value
        }

    /** True while this element owns its [UiLayout]'s keyboard focus. */
    var focused: Boolean = false
        internal set

    /** Whether this element has been clicked and not yet released. */
    var dragging: Boolean = false
        internal set

    /** Whether the pointer is currently inside this element's bounds. */
    var hovering: Boolean = false
        internal set

    /** Whether focusing this element should enable the platform text-input/IME path. */
    internal open val usesPlatformTextInput: Boolean = false

    internal fun focusGained() {
        if (focused) return
        focused = true
        didGainFocus()
        onFocus?.invoke()
    }

    internal fun focusLost() {
        if (!focused) return
        focused = false
        didLoseFocus()
        onBlur?.invoke()
    }

    internal open fun didGainFocus() = Unit

    internal open fun didLoseFocus() = Unit

    init {
        require(tabIndex == null || tabIndex >= -1) {
            "tabIndex must be null or at least -1: $tabIndex"
        }
    }
}

/** Base type for UI elements that arrange child elements. */
sealed class UiContainer(
    tag: String,
    className: Set<String>,
    id: String,
    style: UiStyle,
    children: Iterable<UiElement> = emptyList(),
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
    styleSheets: Iterable<StyleSheet> = emptyList(),
) : UiElement(
    tag = tag,
    className = className,
    id = id,
    style = style,
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
) {
    val children: MutableList<UiElement> = children.toMutableList()

    /**
     * Style sheets scoped to this container and its descendants.
     *
     * [TargetScope] selects this container. An outer container's sheets can style a nested
     * component root but do not enter that component's descendants. Mutating this list after
     * layout requires calling [UiLayout.relayout].
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
     * callbacks created by the component renderer. [content] is built at most once per render at
     * the position chosen by the component.
     */
    fun <T : UiElement> component(
        component: UiComponent<T>,
        key: Any? = null,
        style: UiStyle = UiStyle(),
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: UiContent = {},
    ): T = mountComponent(
        component = component,
        key = key,
        resolveStyle = { style },
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
     * callbacks created by the component renderer. [content] is built at most once per render at
     * the position chosen by the component.
     */
    fun <T : UiElement> component(
        component: UiComponent<T>,
        key: Any? = null,
        style: (T) -> UiStyle,
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        content: UiContent = {},
    ): T = mountComponent(
        component = component,
        key = key,
        resolveStyle = style,
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        content = content,
    )

    private fun <T : UiElement> mountComponent(
        component: UiComponent<T>,
        key: Any?,
        resolveStyle: (T) -> UiStyle,
        tabIndex: Int?,
        onFocus: (() -> Unit)?,
        onBlur: (() -> Unit)?,
        onKeyPressed: ((KeyEvent) -> Unit)?,
        onClick: ((MouseButtonEvent) -> Unit)?,
        onMouseMove: ((x: Double, y: Double) -> Unit)?,
        onDrag: ((MouseButtonEvent) -> Unit)?,
        onMouseOver: (() -> Unit)?,
        onMouseOut: (() -> Unit)?,
        content: UiContent,
    ): T {
        return component.mount(key, content).also { root ->
            root.addStyleOverrides { element ->
                @Suppress("UNCHECKED_CAST")
                resolveStyle(element as T)
            }
            tabIndex?.let { root.tabIndex = it }
            onFocus?.let { root.onFocus = it }
            onBlur?.let { root.onBlur = it }
            onKeyPressed?.let { root.onKeyPressed = it }
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
            tabIndex = tabIndex,
            onFocus = onFocus,
            onBlur = onBlur,
            onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
            tabIndex = tabIndex,
            onFocus = onFocus,
            onBlur = onBlur,
            onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
            text = text,
            style = style,
            tabIndex = tabIndex,
            onFocus = onFocus,
            onBlur = onBlur,
            onKeyPressed = onKeyPressed,
            onClick = onClick,
            onMouseMove = onMouseMove,
            onDrag = onDrag,
            onMouseOver = onMouseOver,
            onMouseOut = onMouseOut,
            tag = tag,
            className = className,
            id = id,
        ),
    )

    /** Creates a paragraph with [className] as its single, unsplit class name. */
    fun p(
        text: String,
        style: UiStyle = UiStyle(),
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
            text = text,
            tabIndex = tabIndex,
            onFocus = onFocus,
            onBlur = onBlur,
            onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: Set<String> = emptySet(),
        id: String = "",
    ): Paragraph = p(
        text = text,
        style = style,
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = className,
        id = id,
    )

    /** Alias of [p] with [className] as its single, unsplit class name. */
    fun paragraph(
        text: String,
        style: UiStyle = UiStyle(),
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
        onClick: ((MouseButtonEvent) -> Unit)? = null,
        onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
        onDrag: ((MouseButtonEvent) -> Unit)? = null,
        onMouseOver: (() -> Unit)? = null,
        onMouseOut: (() -> Unit)? = null,
        tag: String = "p",
        className: Set<String> = emptySet(),
        id: String = "",
    ): Paragraph = p(
        text = text,
        style = style,
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = className,
        id = id,
    )

    /** Dynamic-style alias of [p] with [className] as its single, unsplit class name. */
    fun paragraph(
        text: String,
        style: (Paragraph) -> UiStyle,
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
    tag = tag,
    className = className,
    id = id,
    style = style,
    children = children,
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    styleSheets = styleSheets,
) {
    /** Creates a div with [className] as its single, unsplit class name. */
    constructor(
        style: UiStyle = UiStyle(),
        children: Iterable<UiElement> = emptyList(),
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    override var onClick: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    override var onDrag: ((MouseButtonEvent) -> Unit)? = null,
    override var onMouseOver: (() -> Unit)? = null,
    override var onMouseOut: (() -> Unit)? = null,
    tag: String = "p",
    className: Set<String> = emptySet(),
    id: String = "",
) : UiElement(
    tag = tag,
    className = className,
    id = id,
    style = style,
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
) {
    /** Creates a paragraph with [className] as its single, unsplit class name. */
    constructor(
        text: String,
        style: UiStyle = UiStyle(),
        tabIndex: Int? = null,
        onFocus: (() -> Unit)? = null,
        onBlur: (() -> Unit)? = null,
        onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
        tabIndex = tabIndex,
        onFocus = onFocus,
        onBlur = onBlur,
        onKeyPressed = onKeyPressed,
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
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
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
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
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
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
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
    tabIndex: Int? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
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
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
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
    setStyleProvider { element ->
        @Suppress("UNCHECKED_CAST")
        provider(element as T)
    }
}
