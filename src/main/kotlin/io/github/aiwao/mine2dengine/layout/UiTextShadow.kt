package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.validateShadowParameters

/**
 * A configurable glyph shadow inherited by descendant text without affecting layout bounds.
 *
 * Positive blur uses a bounded multi-sample approximation. Set the containing [UiStyle]'s
 * [UiStyle.dropShadow] to false to disable this shadow for that element and its descendants.
 */
data class UiTextShadow(
    val color: Int = 0x80000000.toInt(),
    val offsetX: Float = 1f,
    val offsetY: Float = 1f,
    val blurRadius: Float = 0f,
) {
    init {
        validateShadowParameters("Text shadow", offsetX, offsetY, blurRadius)
    }
}
