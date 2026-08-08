package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.validateShadowParameters

/**
 * One CSS-compatible `filter: drop-shadow()` applied to an element's composited pixels.
 *
 * Unlike [UiBoxShadow], the shape is taken from the alpha channel of the element's background,
 * text, and descendants. Offsets and blur radius use GUI units. The blur radius must be
 * non-negative; offsets may be negative.
 */
data class UiDropShadow(
    val color: Int = 0x80000000.toInt(),
    val offsetX: Float = 0f,
    val offsetY: Float = 2f,
    val blurRadius: Float = 4f,
) {
    init {
        validateShadowParameters("Drop shadow", offsetX, offsetY, blurRadius)
    }
}
