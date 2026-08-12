package io.github.aiwao.mine2dengine.layout

/**
 * Mutable, rendering-independent editing state for a single-line text input.
 *
 * Indices are UTF-16 offsets, matching Kotlin strings and Minecraft input events. Every mutation
 * keeps the caret and selection anchor on code-point boundaries so a surrogate pair is never split.
 */
internal class TextInputEditor(
    initialValue: String,
    maxLength: Int,
) {
    var value: String = truncateToUtf16Length(initialValue, maxLength)
        private set

    var caret: Int = value.length
        private set

    var anchor: Int = caret
        private set

    val selectionStart: Int
        get() = minOf(caret, anchor)

    val selectionEnd: Int
        get() = maxOf(caret, anchor)

    val hasSelection: Boolean
        get() = caret != anchor

    fun setValue(value: String, maxLength: Int): Boolean {
        val truncated = truncateToUtf16Length(value, maxLength)
        val changed = this.value != truncated
        this.value = truncated
        caret = caret.coerceAtMost(truncated.length).toCodePointBoundary(truncated)
        anchor = anchor.coerceAtMost(truncated.length).toCodePointBoundary(truncated)
        return changed
    }

    fun setSelectionRange(start: Int, end: Int) {
        anchor = start.coerceIn(0, value.length).toCodePointBoundary(value)
        caret = end.coerceIn(0, value.length).toCodePointBoundary(value)
    }

    fun selectAll() {
        anchor = 0
        caret = value.length
    }

    fun selectedText(): String = value.substring(selectionStart, selectionEnd)

    fun moveLeft(extendSelection: Boolean, byWord: Boolean = false) {
        if (!extendSelection && hasSelection) {
            moveTo(selectionStart, extendSelection = false)
            return
        }
        val target = if (byWord) previousWordBoundary(value, caret) else previousBoundary(value, caret)
        moveTo(target, extendSelection)
    }

    fun moveRight(extendSelection: Boolean, byWord: Boolean = false) {
        if (!extendSelection && hasSelection) {
            moveTo(selectionEnd, extendSelection = false)
            return
        }
        val target = if (byWord) nextWordBoundary(value, caret) else nextBoundary(value, caret)
        moveTo(target, extendSelection)
    }

    fun moveToStart(extendSelection: Boolean) {
        moveTo(0, extendSelection)
    }

    fun moveToEnd(extendSelection: Boolean) {
        moveTo(value.length, extendSelection)
    }

    fun moveTo(index: Int, extendSelection: Boolean) {
        val target = index.coerceIn(0, value.length).toCodePointBoundary(value)
        caret = target
        if (!extendSelection) anchor = target
    }

    fun selectWordAt(index: Int) {
        if (value.isEmpty()) {
            moveTo(0, extendSelection = false)
            return
        }
        var position = index.coerceIn(0, value.length).toCodePointBoundary(value)
        if (position == value.length) position = previousBoundary(value, position)
        val codePoint = value.codePointAt(position)
        val category = wordCategory(codePoint)

        var start = position
        while (start > 0) {
            val previous = previousBoundary(value, start)
            if (wordCategory(value.codePointAt(previous)) != category) break
            start = previous
        }

        var end = nextBoundary(value, position)
        while (end < value.length && wordCategory(value.codePointAt(end)) == category) {
            end = nextBoundary(value, end)
        }
        anchor = start
        caret = end
    }

    fun replaceSelection(text: String, maxLength: Int): Boolean {
        val start = selectionStart
        val end = selectionEnd
        val available = (maxLength - (value.length - (end - start))).coerceAtLeast(0)
        val inserted = truncateToUtf16Length(text, available)
        if (start == end && inserted.isEmpty()) return false

        val replacement = buildString(value.length - (end - start) + inserted.length) {
            append(value, 0, start)
            append(inserted)
            append(value, end, value.length)
        }
        val changed = replacement != value
        value = replacement
        caret = start + inserted.length
        anchor = caret
        return changed
    }

    fun deleteBackward(byWord: Boolean): Boolean {
        if (hasSelection) return replaceSelection("", Int.MAX_VALUE)
        if (caret == 0) return false
        val start = if (byWord) previousWordBoundary(value, caret) else previousBoundary(value, caret)
        anchor = start
        return replaceSelection("", Int.MAX_VALUE)
    }

    fun deleteForward(byWord: Boolean): Boolean {
        if (hasSelection) return replaceSelection("", Int.MAX_VALUE)
        if (caret == value.length) return false
        val end = if (byWord) nextWordBoundary(value, caret) else nextBoundary(value, caret)
        anchor = end
        return replaceSelection("", Int.MAX_VALUE)
    }
}

internal fun truncateToUtf16Length(value: String, maxLength: Int): String {
    require(maxLength >= 0) { "maxLength must be non-negative: $maxLength" }
    if (value.length <= maxLength) return value
    var end = maxLength
    if (
        end > 0 && end < value.length &&
        value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()
    ) {
        end--
    }
    return value.substring(0, end)
}

private fun Int.toCodePointBoundary(value: String): Int =
    if (this > 0 && this < value.length && value[this - 1].isHighSurrogate() && value[this].isLowSurrogate()) {
        this - 1
    } else {
        this
    }

private fun previousBoundary(value: String, index: Int): Int =
    if (index <= 0) 0 else value.offsetByCodePoints(index, -1)

private fun nextBoundary(value: String, index: Int): Int =
    if (index >= value.length) value.length else value.offsetByCodePoints(index, 1)

private enum class WordCategory {
    WHITESPACE,
    WORD,
    PUNCTUATION,
}

private fun wordCategory(codePoint: Int): WordCategory = when {
    Character.isWhitespace(codePoint) -> WordCategory.WHITESPACE
    Character.isLetterOrDigit(codePoint) || codePoint == '_'.code -> WordCategory.WORD
    else -> WordCategory.PUNCTUATION
}

private fun previousWordBoundary(value: String, index: Int): Int {
    var cursor = index
    while (cursor > 0) {
        val previous = previousBoundary(value, cursor)
        if (wordCategory(value.codePointAt(previous)) != WordCategory.WHITESPACE) break
        cursor = previous
    }
    if (cursor == 0) return 0

    val category = wordCategory(value.codePointAt(previousBoundary(value, cursor)))
    while (cursor > 0) {
        val previous = previousBoundary(value, cursor)
        if (wordCategory(value.codePointAt(previous)) != category) break
        cursor = previous
    }
    return cursor
}

private fun nextWordBoundary(value: String, index: Int): Int {
    var cursor = index
    if (cursor >= value.length) return value.length

    val category = wordCategory(value.codePointAt(cursor))
    while (cursor < value.length && wordCategory(value.codePointAt(cursor)) == category) {
        cursor = nextBoundary(value, cursor)
    }
    while (cursor < value.length && wordCategory(value.codePointAt(cursor)) == WordCategory.WHITESPACE) {
        cursor = nextBoundary(value, cursor)
    }
    return cursor
}
