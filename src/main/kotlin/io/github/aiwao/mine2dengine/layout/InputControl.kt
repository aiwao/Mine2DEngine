package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent

/** Intrinsic content-box metrics supplied by a replaced input control. */
internal data class InputIntrinsicMetrics(
    val width: Float,
    val height: Float,
    val baselineFromTop: Float,
) {
    init {
        require(width.isFinite() && width >= 0f) { "Input width must be non-negative: $width" }
        require(height.isFinite() && height >= 0f) { "Input height must be non-negative: $height" }
        require(baselineFromTop.isFinite() && baselineFromTop >= 0f) {
            "Input baseline must be non-negative: $baselineFromTop"
        }
    }
}

/** Common focus and replaced-element behavior for typed form controls. */
sealed class InputControl(
    style: UiStyle,
    onFocus: (() -> Unit)?,
    onBlur: (() -> Unit)?,
    onClick: ((MouseButtonEvent) -> Unit)?,
    onMouseMove: ((x: Double, y: Double) -> Unit)?,
    onDrag: ((MouseButtonEvent) -> Unit)?,
    onMouseOver: (() -> Unit)?,
    onMouseOut: (() -> Unit)?,
    tag: String,
    className: Set<String>,
    id: String,
) : UiElement(
    tag = tag,
    className = className,
    id = id,
    style = style,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
) {
    var onFocus: (() -> Unit)? = onFocus
    var onBlur: (() -> Unit)? = onBlur

    /** True while this control owns its [UiLayout]'s keyboard focus. */
    var focused: Boolean = false
        internal set

    /** Whether focusing this control should enable the platform text-input/IME path. */
    internal abstract val usesPlatformTextInput: Boolean

    internal abstract fun intrinsicMetrics(
        textMeasurer: () -> UiTextMeasurer,
    ): InputIntrinsicMetrics

    internal abstract fun narration(): String

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
}
