package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.StringUtil

/** A stateful, single-line text control corresponding to HTML `input type="text"`. */
class TextInput(
    value: String = "",
    placeholder: String = "",
    maxLength: Int = Int.MAX_VALUE,
    size: Int = 20,
    var readOnly: Boolean = false,
    style: UiStyle = UiStyle(),
    onInput: ((String) -> Unit)? = null,
    onChange: ((String) -> Unit)? = null,
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
    tag = tag,
    className = className,
    id = id,
    style = style,
    onFocus = onFocus,
    onBlur = onBlur,
    onClick = onClick,
    onMouseMove = onMouseMove,
    onDrag = onDrag,
    onMouseOver = onMouseOver,
    onMouseOut = onMouseOut,
) {
    /** Whether reconciliation treats [value] as controlled by the current render. */
    internal var valueControlled: Boolean = false

    private val editor = TextInputEditor(sanitizeTextInput(value), maxLength)
    private var valueAtFocus: String = editor.value
    private var changedByUserSinceFocus: Boolean = false

    /** Current committed value. Programmatic assignment does not invoke [onInput]. */
    var value: String
        get() = editor.value
        set(value) {
            editor.setValue(sanitizeTextInput(value), maxLength)
            editor.moveToEnd(extendSelection = false)
            horizontalScroll = 0f
            preedit = null
            if (focused) {
                valueAtFocus = editor.value
                changedByUserSinceFocus = false
            }
            restartCaretBlink()
        }

    /** Hint painted while [value] is empty. */
    var placeholder: String = sanitizeTextInput(placeholder)
        set(value) {
            field = sanitizeTextInput(value)
        }

    /** Maximum number of UTF-16 code units accepted by this control. */
    var maxLength: Int = maxLength
        set(value) {
            require(value >= 0) { "maxLength must be non-negative: $value" }
            field = value
            editor.setValue(editor.value, value)
            horizontalScroll = 0f
            preedit = null
            if (focused) {
                valueAtFocus = editor.value
                changedByUserSinceFocus = false
            }
            restartCaretBlink()
        }

    /** Intrinsic width expressed as this many `0` glyphs when CSS width is `auto`. */
    var size: Int = size
        set(value) {
            require(value > 0) { "size must be positive: $value" }
            field = value
        }

    var onInput: ((String) -> Unit)? = onInput
    var onChange: ((String) -> Unit)? = onChange
    /** Color used for placeholder text. */
    var placeholderColor: Int = 0xFF808080.toInt()

    /** Color painted behind the selected range. */
    var selectionColor: Int = 0xFF2F65CA.toInt()

    /** Optional caret color. Null uses the resolved text color. */
    var caretColor: Int? = null

    /** Smaller UTF-16 endpoint of the current selection. */
    val selectionStart: Int
        get() = editor.selectionStart

    /** Larger UTF-16 endpoint of the current selection. */
    val selectionEnd: Int
        get() = editor.selectionEnd

    /** Active UTF-16 caret endpoint; it can be either end of a selection. */
    val caretPosition: Int
        get() = editor.caret

    /** Currently selected committed text. */
    val selectedText: String
        get() = editor.selectedText()

    internal var horizontalScroll: Float = 0f
    internal var preedit: TextInputPreedit? = null
    private var caretBlinkStartedNanos: Long = System.nanoTime()

    init {
        require(maxLength >= 0) { "maxLength must be non-negative: $maxLength" }
        require(size > 0) { "size must be positive: $size" }
    }

    override val usesPlatformTextInput: Boolean = true

    override fun intrinsicMetrics(textMeasurer: () -> UiTextMeasurer): InputIntrinsicMetrics {
        val measurer = textMeasurer()
        return InputIntrinsicMetrics(
            width = measurer.width("0") * size,
            height = measurer.lineHeight,
            baselineFromTop = measurer.baselineFromLineTop,
        )
    }

    override fun narration(): String {
        val label = placeholder.ifBlank { "Text input" }
        return if (value.isEmpty()) label else "$label: $value"
    }

    fun selectAll() {
        editor.selectAll()
        restartCaretBlink()
    }

    fun setSelectionRange(start: Int, end: Int) {
        editor.setSelectionRange(start, end)
        restartCaretBlink()
    }

    override fun didGainFocus() {
        valueAtFocus = value
        changedByUserSinceFocus = false
        preedit = null
        restartCaretBlink()
    }

    override fun didLoseFocus() {
        preedit = null
        restartCaretBlink()
        if (changedByUserSinceFocus && value != valueAtFocus) onChange?.invoke(value)
        changedByUserSinceFocus = false
    }

    internal fun moveLeft(extendSelection: Boolean, byWord: Boolean) {
        editor.moveLeft(extendSelection, byWord)
        preedit = null
        restartCaretBlink()
    }

    internal fun moveRight(extendSelection: Boolean, byWord: Boolean) {
        editor.moveRight(extendSelection, byWord)
        preedit = null
        restartCaretBlink()
    }

    internal fun moveToStart(extendSelection: Boolean) {
        editor.moveToStart(extendSelection)
        preedit = null
        restartCaretBlink()
    }

    internal fun moveToEnd(extendSelection: Boolean) {
        editor.moveToEnd(extendSelection)
        preedit = null
        restartCaretBlink()
    }

    internal fun moveTo(index: Int, extendSelection: Boolean) {
        editor.moveTo(index, extendSelection)
        preedit = null
        restartCaretBlink()
    }

    internal fun selectWordAt(index: Int) {
        editor.selectWordAt(index)
        preedit = null
        restartCaretBlink()
    }

    internal fun insertUserText(text: String): Boolean = mutateFromUser {
        editor.replaceSelection(sanitizeTextInput(text), maxLength)
    }

    internal fun deleteBackward(byWord: Boolean): Boolean = mutateFromUser {
        editor.deleteBackward(byWord)
    }

    internal fun deleteForward(byWord: Boolean): Boolean = mutateFromUser {
        editor.deleteForward(byWord)
    }

    internal fun cutSelection(): Boolean = mutateFromUser {
        editor.replaceSelection("", maxLength)
    }

    internal fun updatePreedit(text: String, caretPosition: Int) {
        if (readOnly || disabled || text.isEmpty()) {
            preedit = null
        } else {
            val sanitized = sanitizeTextInput(text)
            preedit = sanitized.takeIf(String::isNotEmpty)?.let {
                TextInputPreedit(
                    text = it,
                    caretPosition = caretPosition.coerceIn(0, it.length).toCodePointBoundary(it),
                )
            }
        }
        restartCaretBlink()
    }

    internal fun clearPreedit() {
        preedit = null
    }

    internal fun isCaretVisible(nowNanos: Long = System.nanoTime()): Boolean {
        val elapsedMillis = (nowNanos - caretBlinkStartedNanos).coerceAtLeast(0L) / 1_000_000L
        return elapsedMillis % 1_000L < 500L
    }

    private inline fun mutateFromUser(mutation: () -> Boolean): Boolean {
        preedit = null
        restartCaretBlink()
        if (readOnly || disabled) return false
        val changed = mutation()
        if (changed) {
            changedByUserSinceFocus = true
            onInput?.invoke(value)
        }
        return changed
    }

    private fun restartCaretBlink() {
        caretBlinkStartedNanos = System.nanoTime()
    }
}

internal data class TextInputPreedit(
    val text: String,
    val caretPosition: Int,
)

private fun sanitizeTextInput(value: String): String =
    StringUtil.filterText(value).replace("\r", "").replace("\n", "")

private fun Int.toCodePointBoundary(value: String): Int {
    if (this <= 0 || this >= value.length) return this
    return if (value[this - 1].isHighSurrogate() && value[this].isLowSurrogate()) this - 1 else this
}

/** Adds a single-line text input to this container. */
fun UiContainer.input(
    value: String? = null,
    placeholder: String = "",
    maxLength: Int = Int.MAX_VALUE,
    size: Int = 20,
    readOnly: Boolean = false,
    style: UiStyle = UiStyle(),
    onInput: ((String) -> Unit)? = null,
    onChange: ((String) -> Unit)? = null,
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
    defaultValue: String = "",
): TextInput = add(
    TextInput(
        value = value ?: defaultValue,
        placeholder = placeholder,
        maxLength = maxLength,
        size = size,
        readOnly = readOnly,
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
).also { it.valueControlled = value != null }

/** Adds a dynamically styled single-line text input to this container. */
fun UiContainer.input(
    value: String? = null,
    placeholder: String = "",
    maxLength: Int = Int.MAX_VALUE,
    size: Int = 20,
    readOnly: Boolean = false,
    style: (TextInput) -> UiStyle,
    onInput: ((String) -> Unit)? = null,
    onChange: ((String) -> Unit)? = null,
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
    defaultValue: String = "",
): TextInput = input(
    value = value,
    defaultValue = defaultValue,
    placeholder = placeholder,
    maxLength = maxLength,
    size = size,
    readOnly = readOnly,
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
).also { element ->
    element.setStyleProvider { current -> style(current as TextInput) }
}

/** Creates a single-line text input as the root of a UI tree. */
fun input(
    value: String? = null,
    placeholder: String = "",
    maxLength: Int = Int.MAX_VALUE,
    size: Int = 20,
    readOnly: Boolean = false,
    style: UiStyle = UiStyle(),
    onInput: ((String) -> Unit)? = null,
    onChange: ((String) -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    tag: String = "input",
    className: Set<String> = emptySet(),
    id: String = "",
    defaultValue: String = "",
): TextInput = TextInput(
    value = value ?: defaultValue,
    placeholder = placeholder,
    maxLength = maxLength,
    size = size,
    readOnly = readOnly,
    style = style,
    onInput = onInput,
    onChange = onChange,
    onFocus = onFocus,
    onBlur = onBlur,
    tag = tag,
    className = className,
    id = id,
).also { it.valueControlled = value != null }
