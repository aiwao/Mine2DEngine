package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent
import java.math.BigDecimal
import java.math.RoundingMode

/** The physical direction in which a [RangeInput] lays out its track. */
enum class RangeOrientation {
    HORIZONTAL,
    VERTICAL,
}

/**
 * A stateful numeric slider corresponding to HTML `input type="range"`.
 *
 * A null constructor [value] selects the sanitized midpoint of [min] and [max]. A null [step]
 * corresponds to HTML `step="any"`; keyboard input then uses one hundredth of the range.
 */
class RangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    var orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    var label: String = "Range input",
    var valueText: (Double) -> String = ::defaultRangeValueText,
    style: UiStyle = UiStyle(),
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
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
    onFocus = onFocus,
    onBlur = onBlur,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    tag = tag,
    className = className,
    id = id,
) {
    private val model = RangeInputModel(value, min, max, step)
    private var valueAtCommit: Double = model.value
    private var changedByUserSinceCommit: Boolean = false
    private var userEditActive: Boolean = false

    /** Current finite, clamped, and step-aligned value. Programmatic assignment is silent. */
    var value: Double
        get() = model.value
        set(value) {
            model.setValue(value)
            resetUserEdit()
        }

    /** Inclusive lower bound. It must be finite and no greater than [max]. */
    var min: Double
        get() = model.min
        set(value) {
            model.setMinimum(value)
            resetUserEdit()
        }

    /** Inclusive upper bound. It must be finite and no less than [min]. */
    var max: Double
        get() = model.max
        set(value) {
            model.setMaximum(value)
            resetUserEdit()
        }

    /** Positive allowed-value interval, or null for continuous pointer input. */
    var step: Double?
        get() = model.step
        set(value) {
            model.setStep(value)
            resetUserEdit()
        }

    var onInput: ((Double) -> Unit)? = onInput
    var onChange: ((Double) -> Unit)? = onChange

    /** Color of the unfilled track. */
    var trackColor: Int = 0xFF555555.toInt()

    /** Color of the track between the minimum and the current value. */
    var activeTrackColor: Int = 0xFF4F8CFF.toInt()

    /** Color of the draggable thumb. */
    var thumbColor: Int = 0xFFE0E0E0.toInt()

    /** Color drawn around the thumb while this input owns keyboard focus. */
    var focusColor: Int = 0xFFFFFFFF.toInt()

    override val usesPlatformTextInput: Boolean = false

    override fun intrinsicMetrics(textMeasurer: () -> UiTextMeasurer): InputIntrinsicMetrics =
        when (orientation) {
            RangeOrientation.HORIZONTAL -> InputIntrinsicMetrics(
                width = DEFAULT_LENGTH,
                height = DEFAULT_THICKNESS,
                baselineFromTop = DEFAULT_THICKNESS,
            )

            RangeOrientation.VERTICAL -> InputIntrinsicMetrics(
                width = DEFAULT_THICKNESS,
                height = DEFAULT_LENGTH,
                baselineFromTop = DEFAULT_LENGTH,
            )
        }

    override fun narration(): String = "$label: ${valueText(value)}"

    override fun didGainFocus() {
        resetUserEdit()
    }

    override fun didLoseFocus() {
        commitUserEdit()
    }

    internal fun fraction(): Double = model.fraction()

    internal fun beginUserEdit() {
        if (disabled || userEditActive) return
        valueAtCommit = value
        changedByUserSinceCommit = false
        userEditActive = true
    }

    internal fun setFromUser(value: Double): Boolean {
        if (disabled) return false
        beginUserEdit()
        val changed = model.setValue(value)
        if (changed) {
            changedByUserSinceCommit = true
            onInput?.invoke(this.value)
        }
        return changed
    }

    internal fun adjustFromKeyboard(steps: Int): Boolean {
        if (disabled) return false
        beginUserEdit()
        val changed = model.increment(steps)
        if (changed) {
            changedByUserSinceCommit = true
            onInput?.invoke(value)
        }
        commitUserEdit()
        return changed
    }

    internal fun setToMinimumFromKeyboard(): Boolean = setAndCommitFromKeyboard(min)

    internal fun setToMaximumFromKeyboard(): Boolean = setAndCommitFromKeyboard(max)

    internal fun commitUserEdit() {
        if (!userEditActive) return
        if (changedByUserSinceCommit && value != valueAtCommit) {
            onChange?.invoke(value)
        }
        valueAtCommit = value
        changedByUserSinceCommit = false
        userEditActive = false
    }

    private fun setAndCommitFromKeyboard(value: Double): Boolean {
        beginUserEdit()
        val changed = setFromUser(value)
        commitUserEdit()
        return changed
    }

    private fun resetUserEdit() {
        valueAtCommit = value
        changedByUserSinceCommit = false
        userEditActive = false
    }

    companion object {
        const val DEFAULT_LENGTH: Float = 100f
        const val DEFAULT_THICKNESS: Float = 20f
        internal const val TRACK_THICKNESS: Float = 4f
        internal const val THUMB_RADIUS: Float = 5f
        internal const val THUMB_SEGMENTS: Int = 20
    }
}

/** Numeric state and HTML-like range sanitization independent of layout and Minecraft events. */
internal class RangeInputModel(
    value: Double?,
    min: Double,
    max: Double,
    step: Double?,
) {
    var min: Double = min
        private set
    var max: Double = max
        private set
    var step: Double? = step
        private set
    var value: Double
        private set

    init {
        validateBounds(min, max)
        validateStep(step)
        val initialValue = value ?: min / 2.0 + max / 2.0
        require(initialValue.isFinite()) { "Range value must be finite: $initialValue" }
        this.value = sanitize(initialValue)
    }

    fun setValue(value: Double): Boolean {
        require(value.isFinite()) { "Range value must be finite: $value" }
        val sanitized = sanitize(value)
        if (sanitized == this.value) return false
        this.value = sanitized
        return true
    }

    fun setMinimum(min: Double) {
        validateBounds(min, max)
        this.min = min
        value = sanitize(value)
    }

    fun setMaximum(max: Double) {
        validateBounds(min, max)
        this.max = max
        value = sanitize(value)
    }

    fun setStep(step: Double?) {
        validateStep(step)
        this.step = step
        value = sanitize(value)
    }

    fun increment(steps: Int): Boolean {
        if (steps == 0 || min == max) return false
        val amount = step ?: (max - min) / 100.0
        val candidate = value + amount * steps
        return setValue(
            when {
                candidate.isFinite() -> candidate
                steps > 0 -> max
                else -> min
            },
        )
    }

    fun fraction(): Double = if (min == max) {
        0.0
    } else {
        ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun sanitize(candidate: Double): Double {
        val clamped = candidate.coerceIn(min, max)
        val fixedStep = step ?: return clamped.normalizeNegativeZero()
        if (min == max) return min.normalizeNegativeZero()

        val minimum = BigDecimal.valueOf(min)
        val maximum = BigDecimal.valueOf(max)
        val interval = BigDecimal.valueOf(fixedStep)
        val offset = BigDecimal.valueOf(clamped).subtract(minimum)
        val stepIndex = offset.divide(interval, 0, RoundingMode.HALF_UP)
        var snapped = minimum.add(interval.multiply(stepIndex))
        if (snapped > maximum) {
            val maximumIndex = maximum.subtract(minimum).divide(interval, 0, RoundingMode.FLOOR)
            snapped = minimum.add(interval.multiply(maximumIndex))
        }
        return snapped.toDouble().coerceIn(min, max).normalizeNegativeZero()
    }

    private fun validateBounds(min: Double, max: Double) {
        require(min.isFinite()) { "Range minimum must be finite: $min" }
        require(max.isFinite()) { "Range maximum must be finite: $max" }
        require(min <= max) { "Range minimum must not exceed maximum: $min > $max" }
    }

    private fun validateStep(step: Double?) {
        require(step == null || step.isFinite() && step > 0.0) {
            "Range step must be null or finite and positive: $step"
        }
    }
}

private fun Double.normalizeNegativeZero(): Double = if (this == 0.0) 0.0 else this

private fun defaultRangeValueText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** Adds a numeric range input to this container. */
fun UiContainer.rangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (Double) -> String = ::defaultRangeValueText,
    style: UiStyle = UiStyle(),
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
): RangeInput = add(
    RangeInput(
        value = value,
        min = min,
        max = max,
        step = step,
        orientation = orientation,
        label = label,
        valueText = valueText,
        style = style,
        onInput = onInput,
        onChange = onChange,
        onFocus = onFocus,
        onBlur = onBlur,
        onClick = onClick,
        onMouseMove = onMouseMove,
        onDrag = onDrag,
        onMouseOver = onMouseOver,
        onMouseOut = onMouseOut,
        tag = tag,
        className = className,
        id = id,
    ),
)

/** Adds a dynamically styled numeric range input to this container. */
fun UiContainer.rangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (Double) -> String = ::defaultRangeValueText,
    style: (RangeInput) -> UiStyle,
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    onClick: ((MouseButtonEvent) -> Unit)? = null,
    onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    onDrag: ((MouseButtonEvent) -> Unit)? = null,
    onMouseOver: (() -> Unit)? = null,
    onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
): RangeInput = rangeInput(
    value = value,
    min = min,
    max = max,
    step = step,
    orientation = orientation,
    label = label,
    valueText = valueText,
    onInput = onInput,
    onChange = onChange,
    onFocus = onFocus,
    onBlur = onBlur,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
    tag = tag,
    className = className,
    id = id,
).also { element -> element.setStyleProvider { style(element) } }

/** Creates a numeric range input as the root of a UI tree. */
fun rangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (Double) -> String = ::defaultRangeValueText,
    style: UiStyle = UiStyle(),
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
): RangeInput = RangeInput(
    value = value,
    min = min,
    max = max,
    step = step,
    orientation = orientation,
    label = label,
    valueText = valueText,
    style = style,
    onInput = onInput,
    onChange = onChange,
    onFocus = onFocus,
    onBlur = onBlur,
    tag = tag,
    className = className,
    id = id,
)
