package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

/** The physical direction in which a [RangeInput] lays out its track. */
enum class RangeOrientation {
    HORIZONTAL,
    VERTICAL,
}

/**
 * A stateful typed numeric slider corresponding to HTML `input type="range"`.
 *
 * A null constructor [value] selects the sanitized midpoint of [min] and [max]. A null [step]
 * corresponds to HTML `step="any"`; keyboard input then uses one hundredth of the range, or at
 * least one for an [Int] range.
 */
class RangeInput<T : Number>(
    val numberType: RangeNumberType<T>,
    value: T? = null,
    min: T = numberType.defaultMin,
    max: T = numberType.defaultMax,
    step: T? = numberType.defaultStep,
    var orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    var label: String = "Range input",
    var valueText: (T) -> String = numberType::format,
    style: UiStyle = UiStyle(),
    onInput: ((T) -> Unit)? = null,
    onChange: ((T) -> Unit)? = null,
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

    private val model = RangeInputModel(numberType, value, min, max, step)
    private var valueAtCommit: T = model.value
    private var changedByUserSinceCommit: Boolean = false
    private var userEditActive: Boolean = false

    /** Current finite, clamped, and step-aligned value. Programmatic assignment is silent. */
    var value: T
        get() = model.value
        set(value) {
            model.setValue(value)
            resetUserEdit()
        }

    /** Inclusive lower bound. It must be finite and no greater than [max]. */
    var min: T
        get() = model.min
        set(value) {
            model.setMinimum(value)
            resetUserEdit()
        }

    /** Inclusive upper bound. It must be finite and no less than [min]. */
    var max: T
        get() = model.max
        set(value) {
            model.setMaximum(value)
            resetUserEdit()
        }

    /** Positive allowed-value interval, or null to disable step alignment. */
    var step: T?
        get() = model.step
        set(value) {
            model.setStep(value)
            resetUserEdit()
        }

    var onInput: ((T) -> Unit)? = onInput
    var onChange: ((T) -> Unit)? = onChange

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

    internal fun setFromUser(value: T): Boolean {
        if (disabled) return false
        beginUserEdit()
        return dispatchUserMutation { model.setValue(value) }
    }

    internal fun setFromUserFraction(fraction: Double): Boolean {
        if (disabled) return false
        beginUserEdit()
        return dispatchUserMutation { model.setFromFraction(fraction) }
    }

    internal fun adjustFromKeyboard(steps: Int): Boolean {
        if (disabled) return false
        beginUserEdit()
        val changed = dispatchUserMutation { model.increment(steps) }
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

    private inline fun dispatchUserMutation(mutation: () -> Boolean): Boolean {
        val changed = mutation()
        if (changed) {
            changedByUserSinceCommit = true
            onInput?.invoke(value)
        }
        return changed
    }

    private fun setAndCommitFromKeyboard(value: T): Boolean {
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

    internal fun patchConfigurationFrom(
        next: RangeInput<*>,
        undo: MutableList<() -> Unit>,
        afterCommit: MutableList<() -> Unit>,
    ) {
        check(numberType === next.numberType) { "Range input number type changed during reconciliation" }
        @Suppress("UNCHECKED_CAST")
        next as RangeInput<T>
        val oldValue = value
        val oldMin = min
        val oldMax = max
        val oldStep = step
        val oldValueControlled = valueControlled
        val oldOrientation = orientation
        val oldLabel = label
        val oldValueText = valueText
        val oldOnInput = onInput
        val oldOnChange = onChange
        val configurationChanged = oldMin != next.min || oldMax != next.max || oldStep != next.step
        undo += {
            orientation = oldOrientation
            label = oldLabel
            valueText = oldValueText
            onInput = oldOnInput
            onChange = oldOnChange
            if (configurationChanged) {
                model.setConfiguration(oldMin, oldMax, oldStep)
                value = oldValue
            }
            valueControlled = oldValueControlled
        }
        orientation = next.orientation
        label = next.label
        valueText = next.valueText
        onInput = next.onInput
        onChange = next.onChange
        if (configurationChanged) model.setConfiguration(next.min, next.max, next.step)
        if (next.valueControlled && oldValue != next.value) {
            afterCommit += { value = next.value }
        }
        valueControlled = next.valueControlled
    }

    companion object {
        const val DEFAULT_LENGTH: Float = 100f
        const val DEFAULT_THICKNESS: Float = 20f
        internal const val TRACK_THICKNESS: Float = 4f
        internal const val THUMB_RADIUS: Float = 5f
        internal const val THUMB_SEGMENTS: Int = 20
    }
}

/** Numeric state and range sanitization independent of layout and Minecraft events. */
internal class RangeInputModel<T : Number>(
    private val numberType: RangeNumberType<T>,
    value: T?,
    min: T,
    max: T,
    step: T?,
) {
    var min: T = min
        private set
    var max: T = max
        private set
    var step: T? = step
        private set
    var value: T
        private set

    init {
        numberType.validateConfiguration(min, max, step)
        this.value = value?.let { numberType.sanitize(it, min, max, step) }
            ?: numberType.midpoint(min, max, step)
    }

    fun setValue(value: T): Boolean = setSanitized(
        numberType.sanitize(value, min, max, step),
    )

    fun setMinimum(min: T) {
        numberType.validateConfiguration(min, max, step)
        this.min = min
        value = numberType.sanitize(value, min, max, step)
    }

    fun setMaximum(max: T) {
        numberType.validateConfiguration(min, max, step)
        this.max = max
        value = numberType.sanitize(value, min, max, step)
    }

    fun setStep(step: T?) {
        numberType.validateConfiguration(min, max, step)
        this.step = step
        value = numberType.sanitize(value, min, max, step)
    }

    fun setConfiguration(min: T, max: T, step: T?) {
        numberType.validateConfiguration(min, max, step)
        this.min = min
        this.max = max
        this.step = step
        value = numberType.sanitize(value, min, max, step)
    }

    fun increment(steps: Int): Boolean = setSanitized(
        numberType.increment(value, steps, min, max, step),
    )

    fun setFromFraction(fraction: Double): Boolean = setSanitized(
        numberType.interpolate(fraction, min, max, step),
    )

    fun fraction(): Double = numberType.fraction(value, min, max)

    private fun setSanitized(value: T): Boolean {
        if (value == this.value) return false
        this.value = value
        return true
    }
}

/** Adds a typed numeric range input using an explicit numeric strategy. */
fun <T : Number> UiContainer.rangeInput(
    numberType: RangeNumberType<T>,
    value: T? = null,
    min: T = numberType.defaultMin,
    max: T = numberType.defaultMax,
    step: T? = numberType.defaultStep,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (T) -> String = numberType::format,
    style: UiStyle = UiStyle(),
    onInput: ((T) -> Unit)? = null,
    onChange: ((T) -> Unit)? = null,
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
    defaultValue: T? = null,
): RangeInput<T> = add(
    RangeInput(
        numberType = numberType,
        value = value ?: defaultValue,
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

/** Adds a typed numeric range input selected by its reified number type. */
inline fun <reified T : Number> UiContainer.rangeInput(
    value: T? = null,
    min: T = rangeNumberType<T>().defaultMin,
    max: T = rangeNumberType<T>().defaultMax,
    step: T? = rangeNumberType<T>().defaultStep,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    noinline valueText: (T) -> String = rangeNumberType<T>()::format,
    style: UiStyle = UiStyle(),
    noinline onInput: ((T) -> Unit)? = null,
    noinline onChange: ((T) -> Unit)? = null,
    noinline onFocus: (() -> Unit)? = null,
    noinline onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    noinline onKeyPressed: ((KeyEvent) -> Unit)? = null,
    noinline onClick: ((MouseButtonEvent) -> Unit)? = null,
    noinline onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    noinline onDrag: ((MouseButtonEvent) -> Unit)? = null,
    noinline onMouseOver: (() -> Unit)? = null,
    noinline onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: T? = null,
): RangeInput<T> = rangeInput(
    numberType = rangeNumberType<T>(),
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
    defaultValue = defaultValue,
)

/** Adds a Double range input when no number type is specified. */
fun UiContainer.rangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (Double) -> String = RangeNumberTypes.DOUBLE::format,
    style: UiStyle = UiStyle(),
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
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
    defaultValue: Double? = null,
): RangeInput<Double> = rangeInput(
    numberType = RangeNumberTypes.DOUBLE,
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
    defaultValue = defaultValue,
)

/** Adds a dynamically styled typed range input using an explicit numeric strategy. */
fun <T : Number> UiContainer.rangeInput(
    numberType: RangeNumberType<T>,
    value: T? = null,
    min: T = numberType.defaultMin,
    max: T = numberType.defaultMax,
    step: T? = numberType.defaultStep,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (T) -> String = numberType::format,
    style: (RangeInput<T>) -> UiStyle,
    onInput: ((T) -> Unit)? = null,
    onChange: ((T) -> Unit)? = null,
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
    defaultValue: T? = null,
): RangeInput<T> = rangeInput(
    numberType = numberType,
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
    defaultValue = defaultValue,
).also { element ->
    element.setStyleProvider { current ->
        @Suppress("UNCHECKED_CAST")
        style(current as RangeInput<T>)
    }
}

/** Adds a dynamically styled typed range input selected by its reified number type. */
inline fun <reified T : Number> UiContainer.rangeInput(
    value: T? = null,
    min: T = rangeNumberType<T>().defaultMin,
    max: T = rangeNumberType<T>().defaultMax,
    step: T? = rangeNumberType<T>().defaultStep,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    noinline valueText: (T) -> String = rangeNumberType<T>()::format,
    noinline style: (RangeInput<T>) -> UiStyle,
    noinline onInput: ((T) -> Unit)? = null,
    noinline onChange: ((T) -> Unit)? = null,
    noinline onFocus: (() -> Unit)? = null,
    noinline onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    noinline onKeyPressed: ((KeyEvent) -> Unit)? = null,
    noinline onClick: ((MouseButtonEvent) -> Unit)? = null,
    noinline onMouseMove: ((x: Double, y: Double) -> Unit)? = null,
    noinline onDrag: ((MouseButtonEvent) -> Unit)? = null,
    noinline onMouseOver: (() -> Unit)? = null,
    noinline onMouseOut: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: T? = null,
): RangeInput<T> = rangeInput(
    numberType = rangeNumberType<T>(),
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
    defaultValue = defaultValue,
)

/** Adds a dynamically styled Double range input when no number type is specified. */
fun UiContainer.rangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (Double) -> String = RangeNumberTypes.DOUBLE::format,
    style: (RangeInput<Double>) -> UiStyle,
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
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
    defaultValue: Double? = null,
): RangeInput<Double> = rangeInput(
    numberType = RangeNumberTypes.DOUBLE,
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
    defaultValue = defaultValue,
)

/** Creates a typed numeric range input root using an explicit numeric strategy. */
fun <T : Number> rangeInput(
    numberType: RangeNumberType<T>,
    value: T? = null,
    min: T = numberType.defaultMin,
    max: T = numberType.defaultMax,
    step: T? = numberType.defaultStep,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (T) -> String = numberType::format,
    style: UiStyle = UiStyle(),
    onInput: ((T) -> Unit)? = null,
    onChange: ((T) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: T? = null,
): RangeInput<T> = RangeInput(
    numberType = numberType,
    value = value ?: defaultValue,
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
    tabIndex = tabIndex,
    onKeyPressed = onKeyPressed,
    tag = tag,
    className = className,
    id = id,
).also { it.valueControlled = value != null }

/** Creates a typed numeric range input root selected by its reified number type. */
inline fun <reified T : Number> rangeInput(
    value: T? = null,
    min: T = rangeNumberType<T>().defaultMin,
    max: T = rangeNumberType<T>().defaultMax,
    step: T? = rangeNumberType<T>().defaultStep,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    noinline valueText: (T) -> String = rangeNumberType<T>()::format,
    style: UiStyle = UiStyle(),
    noinline onInput: ((T) -> Unit)? = null,
    noinline onChange: ((T) -> Unit)? = null,
    noinline onFocus: (() -> Unit)? = null,
    noinline onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    noinline onKeyPressed: ((KeyEvent) -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: T? = null,
): RangeInput<T> = rangeInput(
    numberType = rangeNumberType<T>(),
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
    tabIndex = tabIndex,
    onKeyPressed = onKeyPressed,
    tag = tag,
    className = className,
    id = id,
    defaultValue = defaultValue,
)

/** Creates a Double range input root when no number type is specified. */
fun rangeInput(
    value: Double? = null,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double? = 1.0,
    orientation: RangeOrientation = RangeOrientation.HORIZONTAL,
    label: String = "Range input",
    valueText: (Double) -> String = RangeNumberTypes.DOUBLE::format,
    style: UiStyle = UiStyle(),
    onInput: ((Double) -> Unit)? = null,
    onChange: ((Double) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tabIndex: Int? = 0,
    onKeyPressed: ((KeyEvent) -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: Double? = null,
): RangeInput<Double> = rangeInput(
    numberType = RangeNumberTypes.DOUBLE,
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
    tabIndex = tabIndex,
    onKeyPressed = onKeyPressed,
    tag = tag,
    className = className,
    id = id,
    defaultValue = defaultValue,
)
