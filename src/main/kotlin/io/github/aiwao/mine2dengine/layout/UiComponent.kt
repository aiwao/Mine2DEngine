package io.github.aiwao.mine2dengine.layout

/**
 * A reusable factory for a UI element tree.
 *
 * [create] is called once whenever this component is added to a [UiContainer]. Each call must
 * return a new element tree so that interaction state is not shared between mounted instances.
 */
fun interface UiComponent<out T : UiElement> {
    fun create(): T
}

/** Creates a reusable [UiComponent] from [factory]. */
fun <T : UiElement> uiComponent(factory: () -> T): UiComponent<T> = UiComponent(factory)
