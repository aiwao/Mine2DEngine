package io.github.aiwao.mine2dengine.layout

/**
 * A reusable factory for a UI element tree.
 *
 * [create] is called once whenever this component is added to a [UiContainer]. Each call receives
 * the content supplied at the mount site and must return a new element tree so that interaction
 * state is not shared between mounted instances. The component factory decides where to invoke
 * that content in its tree and may invoke it at most once. [styleSheets] apply to the created root,
 * supplied content, and other descendants, stopping inside a nested component. Global style sheets
 * passed to [LayoutEngine] remain visible across component boundaries.
 */
fun interface UiComponent<out T : UiElement> {
    /** Style sheets scoped to each element tree created by this component, in cascade order. */
    val styleSheets: List<StyleSheet>
        get() = emptyList()

    fun create(content: UiContent): T
}

/** Builds the elements supplied to a component at its mount site. */
typealias UiContent = UiContainer.() -> Unit

/** Creates a reusable [UiComponent] from [factory]. */
fun <T : UiElement> uiComponent(factory: (UiContent) -> T): UiComponent<T> = UiComponent(factory)

/** Creates a reusable [UiComponent] with one component-scoped [styleSheet]. */
fun <T : UiElement> uiComponent(
    styleSheet: StyleSheet,
    factory: (UiContent) -> T,
): UiComponent<T> = uiComponent(listOf(styleSheet), factory)

/** Creates a reusable [UiComponent] with component-scoped [styleSheets]. */
fun <T : UiElement> uiComponent(
    styleSheets: Iterable<StyleSheet>,
    factory: (UiContent) -> T,
): UiComponent<T> {
    val sheets = styleSheets.toList()
    return object : UiComponent<T> {
        override val styleSheets: List<StyleSheet> = sheets

        override fun create(content: UiContent): T = factory(content)
    }
}
