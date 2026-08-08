package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.validateShadowParameters

/** A soft rounded-box shadow painted behind one UI element without affecting its layout bounds. */
data class UiBoxShadow(
    val color: Int = 0x80000000.toInt(),
    val offsetX: Float = 0f,
    val offsetY: Float = 2f,
    val blurRadius: Float = 4f,
    val spreadRadius: Float = 0f,
    val cornerRadius: Float = 0f,
) {
    init {
        validateShadowParameters("Box shadow", offsetX, offsetY, blurRadius)
        require(spreadRadius.isFinite()) {
            "Box shadow spread radius must be finite"
        }
        require(cornerRadius.isFinite() && cornerRadius >= 0f) {
            "Box shadow corner radius must be finite and non-negative"
        }
    }
}
