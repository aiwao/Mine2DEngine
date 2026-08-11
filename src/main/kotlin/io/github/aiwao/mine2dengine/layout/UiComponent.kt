package io.github.aiwao.mine2dengine.layout

/**
 * A reusable factory for a UI element tree.
 *
 * [create] is called once whenever this component is added to a [UiContainer]. Each call must
 * return a new element tree so that interaction state is not shared between mounted instances.
 * [styleSheets] apply to the created root and its descendants, stopping inside a nested component.
 * Global style sheets passed to [LayoutEngine] remain visible across component boundaries.
 */
fun interface UiComponent<out T : UiElement> {
    /** Style sheets scoped to each element tree created by this component, in cascade order. */
    val styleSheets: List<StyleSheet>
        get() = emptyList()

    fun create(): T
}

/** Creates a reusable [UiComponent] from [factory]. */
fun <T : UiElement> uiComponent(factory: () -> T): UiComponent<T> = UiComponent(factory)

/** Creates a reusable [UiComponent] with one component-scoped [styleSheet]. */
fun <T : UiElement> uiComponent(
    styleSheet: StyleSheet,
    factory: () -> T,
): UiComponent<T> = uiComponent(listOf(styleSheet), factory)

/** Creates a reusable [UiComponent] with component-scoped [styleSheets]. */
fun <T : UiElement> uiComponent(
    styleSheets: Iterable<StyleSheet>,
    factory: () -> T,
): UiComponent<T> {
    val sheets = styleSheets.toList()
    return object : UiComponent<T> {
        override val styleSheets: List<StyleSheet> = sheets

        override fun create(): T = factory()
    }
}
