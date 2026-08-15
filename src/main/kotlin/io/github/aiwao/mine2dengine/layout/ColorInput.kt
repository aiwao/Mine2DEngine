package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/** A stateful, opaque RGB color control corresponding to HTML `input type="color"`. */
class ColorInput(
    value: Int = 0xFF000000.toInt(),
    var label: String = "Color input",
    style: UiStyle = UiStyle(),
    onInput: ((Int) -> Unit)? = null,
    onChange: ((Int) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
) : InputControl(
    style = style,
    tabIndex = tabIndex,
    onFocus = onFocus,
    onBlur = onBlur,
    onKeyPressed = onKeyPressed,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    tag = tag,
    className = className,
    id = id,
) {
    /** Whether reconciliation synchronizes [value] from each render. */
    internal var valueControlled: Boolean = false

    private var storedValue: Int = value.toOpaqueColor()
    private var valueAtCommit: Int = storedValue
    private var valueAtPickerOpen: Int = storedValue
    private var changedByUserSinceCommit: Boolean = false
    private var pickerHue: Float = storedValue.toHsv().hue

    /** Current color in Minecraft `0xAARRGGBB` format. Alpha is always normalized to `0xFF`. */
    var value: Int
        get() = storedValue
        set(value) {
            storedValue = value.toOpaqueColor()
            pickerHue = storedValue.toHsv().hue
            if (focused) {
                valueAtCommit = storedValue
                valueAtPickerOpen = storedValue
                changedByUserSinceCommit = false
            }
        }

    var onInput: ((Int) -> Unit)? = onInput
    var onChange: ((Int) -> Unit)? = onChange

    /** Whether the color-picker overlay is currently visible. */
    var pickerOpen: Boolean = false
        private set

    override val usesPlatformTextInput: Boolean = false

    override fun intrinsicMetrics(textMeasurer: () -> UiTextMeasurer): InputIntrinsicMetrics =
        InputIntrinsicMetrics(
            width = DEFAULT_WIDTH,
            height = DEFAULT_HEIGHT,
            baselineFromTop = DEFAULT_HEIGHT,
        )

    override fun narration(): String = "$label: ${value.toHexRgb()}"

    override fun didGainFocus() {
        valueAtCommit = value
        changedByUserSinceCommit = false
    }

    override fun didLoseFocus() {
        pickerOpen = false
        commitUserChange()
    }

    internal fun openPicker() {
        if (disabled || pickerOpen) return
        pickerOpen = true
        valueAtPickerOpen = value
        pickerHue = value.toHsv().hue
    }

    internal fun commitPicker() {
        if (!pickerOpen) return
        pickerOpen = false
        commitUserChange()
    }

    internal fun cancelPicker() {
        if (!pickerOpen) return
        pickerOpen = false
        setFromUser(valueAtPickerOpen)
        changedByUserSinceCommit = value != valueAtCommit
    }

    internal fun updateSaturationValue(saturation: Float, brightness: Float) {
        setFromUser(
            hsvToArgb(
                hue = pickerHue,
                saturation = saturation.coerceIn(0f, 1f),
                value = brightness.coerceIn(0f, 1f),
            ),
        )
    }

    internal fun updateHue(hue: Float) {
        val hsv = value.toHsv()
        pickerHue = hue.normalizedHue()
        setFromUser(hsvToArgb(pickerHue, hsv.saturation, hsv.value))
    }

    internal fun adjustHue(delta: Float) {
        updateHue(pickerHue + delta)
    }

    internal fun pickerState(): ColorHsv {
        val hsv = value.toHsv()
        return hsv.copy(hue = pickerHue)
    }

    private fun setFromUser(value: Int) {
        val normalized = value.toOpaqueColor()
        if (normalized == storedValue) return
        storedValue = normalized
        changedByUserSinceCommit = storedValue != valueAtCommit
        onInput?.invoke(storedValue)
    }

    private fun commitUserChange() {
        if (changedByUserSinceCommit && value != valueAtCommit) {
            onChange?.invoke(value)
        }
        valueAtCommit = value
        changedByUserSinceCommit = false
    }

    companion object {
        const val DEFAULT_WIDTH: Float = 36f
        const val DEFAULT_HEIGHT: Float = 20f
    }
}

internal data class ColorHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal fun Int.toOpaqueColor(): Int = this or 0xFF000000.toInt()

internal fun Int.toHexRgb(): String = "#%06X".format(this and 0xFFFFFF)

internal fun Int.toHsv(): ColorHsv {
    val red = (this ushr 16 and 0xFF) / 255f
    val green = (this ushr 8 and 0xFF) / 255f
    val blue = (this and 0xFF) / 255f
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.normalizedHue()
    return ColorHsv(
        hue = hue,
        saturation = if (maximum == 0f) 0f else delta / maximum,
        value = maximum,
    )
}

internal fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val normalizedHue = hue.normalizedHue()
    val normalizedSaturation = saturation.coerceIn(0f, 1f)
    val normalizedValue = value.coerceIn(0f, 1f)
    val chroma = normalizedValue * normalizedSaturation
    val section = normalizedHue / 60f
    val second = chroma * (1f - abs(section % 2f - 1f))
    val (redPart, greenPart, bluePart) = when (section.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val match = normalizedValue - chroma
    val red = ((redPart + match) * 255f).roundToInt().coerceIn(0, 255)
    val green = ((greenPart + match) * 255f).roundToInt().coerceIn(0, 255)
    val blue = ((bluePart + match) * 255f).roundToInt().coerceIn(0, 255)
    return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
}

private fun Float.normalizedHue(): Float {
    val remainder = this % 360f
    return if (remainder < 0f) remainder + 360f else remainder
}

/** Adds an opaque RGB color input to this container. */
fun UiContainer.colorInput(
    value: Int? = null,
    label: String = "Color input",
    style: UiStyle = UiStyle(),
    onInput: ((Int) -> Unit)? = null,
    onChange: ((Int) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: Int = 0xFF000000.toInt(),
): ColorInput = add(
    ColorInput(
        value = value ?: defaultValue,
        label = label,
        style = style,
        onInput = onInput,
        onChange = onChange,
        onFocus = onFocus,
        onBlur = onBlur,
        tabIndex = tabIndex,
        onKeyPressed = onKeyPressed,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = className,
        id = id,
    ),
).also { it.valueControlled = value != null }

/** Adds a dynamically styled opaque RGB color input to this container. */
fun UiContainer.colorInput(
    value: Int? = null,
    label: String = "Color input",
    style: (ColorInput) -> UiStyle,
    onInput: ((Int) -> Unit)? = null,
    onChange: ((Int) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: Int = 0xFF000000.toInt(),
): ColorInput = colorInput(
    value = value,
    defaultValue = defaultValue,
    label = label,
    onInput = onInput,
    onChange = onChange,
    onFocus = onFocus,
    onBlur = onBlur,
    tabIndex = tabIndex,
    onKeyPressed = onKeyPressed,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    tag = tag,
    className = className,
    id = id,
).also { element ->
    element.setStyleProvider { current -> style(current as ColorInput) }
}

/** Creates an opaque RGB color input as the root of a UI tree. */
fun colorInput(
    value: Int? = null,
    label: String = "Color input",
    style: UiStyle = UiStyle(),
    onInput: ((Int) -> Unit)? = null,
    onChange: ((Int) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: Int = 0xFF000000.toInt(),
): ColorInput = ColorInput(
    value = value ?: defaultValue,
    label = label,
    style = style,
    onInput = onInput,
    onChange = onChange,
    onFocus = onFocus,
    onBlur = onBlur,
    tabIndex = tabIndex,
    onKeyPressed = onKeyPressed,
    tag = tag,
    className = className,
    id = id,
).also { it.valueControlled = value != null }
