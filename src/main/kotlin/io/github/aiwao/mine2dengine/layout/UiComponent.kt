package io.github.aiwao.mine2dengine.layout

/**
 * A reusable, hook-capable definition of a UI element tree.
 *
 * Every call-site mount owns a separate [ComponentInstance]. Components that do not call hooks
 * simply keep an empty hook list and are otherwise mounted and reconciled in exactly the same way
 * as components that call [ComponentScope.useState]. [styleSheets] apply to the component root and
 * its descendants, stopping inside a nested component. Global sheets passed to [LayoutEngine]
 * remain visible across component boundaries.
 */
abstract class UiComponent<out T : UiElement> protected constructor() {
    /** Style sheets scoped to each tree rendered by this component, in cascade order. */
    open val styleSheets: List<StyleSheet> = emptyList()

    /** Describes this component's current host tree. Hooks may only be called from this method. */
    protected abstract fun ComponentScope.render(content: UiContent): T

    internal fun renderWith(scope: ComponentScope, content: UiContent): T =
        scope.render(content)

    /** Creates a standalone mount. Prefer [UiContainer.component] when adding it to a tree. */
    fun create(content: UiContent = {}): T = mount(key = null, content = content)

    internal fun mount(key: Any?, content: UiContent): T {
        ComponentRenderContext.current()?.let { context ->
            context.claim(this, key)?.let { reused ->
                @Suppress("UNCHECKED_CAST")
                return reused.render(content) as T
            }
        }

        val parentContext = ComponentRenderContext.current()
        val instance = ComponentInstance(
            definition = this,
            key = key,
            parent = parentContext?.instance,
            mountOrdinal = parentContext?.lastClaimedOrdinal ?: 0,
            initialContent = content,
        )
        parentContext?.instance?.children?.add(instance)
        val root = try {
            instance.render(content)
        } catch (failure: Throwable) {
            instance.unmount()
            throw failure
        }
        instance.mountedRoot = root
        instance.commitPendingRender()
        @Suppress("UNCHECKED_CAST")
        return root as T
    }
}

/** Builds the elements supplied to a component at its mount site. */
typealias UiContent = UiContainer.() -> Unit

/** Creates a reusable hook-capable [UiComponent] from [renderer]. */
fun <T : UiElement> uiComponent(
    renderer: ComponentScope.(UiContent) -> T,
): UiComponent<T> = LambdaUiComponent(emptyList(), renderer)

/** Creates a reusable [UiComponent] with one component-scoped [styleSheet]. */
fun <T : UiElement> uiComponent(
    styleSheet: StyleSheet,
    renderer: ComponentScope.(UiContent) -> T,
): UiComponent<T> = LambdaUiComponent(listOf(styleSheet), renderer)

/** Creates a reusable [UiComponent] with component-scoped [styleSheets]. */
fun <T : UiElement> uiComponent(
    styleSheets: Iterable<StyleSheet>,
    renderer: ComponentScope.(UiContent) -> T,
): UiComponent<T> = LambdaUiComponent(styleSheets, renderer)

private class LambdaUiComponent<out T : UiElement>(
    styleSheets: Iterable<StyleSheet>,
    private val renderer: ComponentScope.(UiContent) -> T,
) : UiComponent<T>() {
    override val styleSheets: List<StyleSheet> = styleSheets.toList()

    override fun ComponentScope.render(content: UiContent): T = renderer(content)
}
