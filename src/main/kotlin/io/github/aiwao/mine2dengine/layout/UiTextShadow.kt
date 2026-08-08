package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.validateShadowParameters

/**
 * A configurable glyph shadow inherited by descendant text without affecting layout bounds.
 *
 * Its color, offsets, and non-negative blur radius match one CSS `text-shadow`. Use [NONE] on a
 * descendant to explicitly clear an inherited shadow.
 */
data class UiTextShadow(
    val color: Int = 0x80000000.toInt(),
    val offsetX: Float = 1f,
    val offsetY: Float = 1f,
    val blurRadius: Float = 0f,
) {
    companion object {
        /** Explicitly clears an inherited text shadow. */
        @JvmField
        val NONE: UiTextShadow = UiTextShadow(color = 0x00000000)
    }

    init {
        validateShadowParameters("Text shadow", offsetX, offsetY, blurRadius)
    }
}
