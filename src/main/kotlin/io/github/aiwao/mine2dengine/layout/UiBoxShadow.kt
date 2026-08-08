package io.github.aiwao.mine2dengine.layout

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
        require(offsetX.isFinite() && offsetY.isFinite()) {
            "Box shadow offsets must be finite"
        }
        require(blurRadius.isFinite() && blurRadius >= 0f) {
            "Box shadow blur radius must be finite and non-negative"
        }
        require(spreadRadius.isFinite()) {
            "Box shadow spread radius must be finite"
        }
        require(cornerRadius.isFinite() && cornerRadius >= 0f) {
            "Box shadow corner radius must be finite and non-negative"
        }
    }
}
