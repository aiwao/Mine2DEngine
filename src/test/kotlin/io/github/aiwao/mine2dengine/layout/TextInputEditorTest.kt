package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.input.PreeditEvent
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TextInputEditorTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight: Float = 10f
        override val baselineFromLineTop: Float = 7f

        override fun width(text: String): Float = text.codePointCount(0, text.length) * 5f
    }

    private fun layout(root: UiElement): UiLayout = calculateLayout(
        root = root,
        viewport = UiRect(0f, 0f, 100f, 40f),
        textMeasurer = textMeasurer,
    )

    @Test
    fun `selection and deletion never split a surrogate pair`() {
        val editor = TextInputEditor("a😀b", Int.MAX_VALUE)

        editor.setSelectionRange(2, 3)

        assertEquals(1, editor.selectionStart)
        assertEquals(3, editor.selectionEnd)
        assertTrue(editor.replaceSelection("", Int.MAX_VALUE))
        assertEquals("ab", editor.value)
        assertEquals(1, editor.caret)
    }

    @Test
    fun `maximum length truncation never leaves an unmatched surrogate`() {
        val editor = TextInputEditor("a😀b", 2)

        assertEquals("a", editor.value)
        assertEquals(1, editor.caret)
    }

    @Test
    fun `word movement and selection replacement update editing state`() {
        val editor = TextInputEditor("one two", Int.MAX_VALUE)

        editor.moveLeft(extendSelection = false, byWord = true)
        assertEquals(4, editor.caret)
        editor.moveRight(extendSelection = true, byWord = true)
        assertEquals("two", editor.selectedText())
        assertTrue(editor.replaceSelection("three", Int.MAX_VALUE))

        assertEquals("one three", editor.value)
        assertEquals(9, editor.caret)
    }

    @Test
    fun `text input uses replaced intrinsic dimensions instead of its value`() {
        lateinit var input: TextInput
        val root = div {
            input = input(value = "a very long value", size = 4)
        }
        val result = layout(root)
        val node = result.nodeOf(input)!!

        assertEquals(UiDisplay.Box(UiDisplayOutside.INLINE, UiDisplayInside.FLOW_ROOT), node.styleProvider().display)
        assertEquals(20f, node.contentBounds.width)
        assertEquals(10f, node.contentBounds.height)

        input.value = "an even longer value that does not trigger layout"

        assertSame(node, result.nodeOf(input))
        assertEquals(20f, result.nodeOf(input)!!.contentBounds.width)
    }

    @Test
    fun `explicit input height exposes the vertically centered text baseline to inline layout`() {
        lateinit var input: TextInput
        lateinit var label: Paragraph
        val root = div {
            input = input(style = UiStyle(height = 20f.px), size = 2)
            label = p("x", style = UiStyle(display = UiDisplay.INLINE))
        }

        val result = layout(root)

        assertEquals(0f, result.nodeOf(input)!!.bounds.top)
        assertEquals(5f, result.nodeOf(label)!!.bounds.top)
        assertEquals(20f, result.root.contentBounds.height)
    }

    @Test
    fun `click focus committed character and blur dispatch input and change`() {
        val inputs = mutableListOf<String>()
        val changes = mutableListOf<String>()
        lateinit var input: TextInput
        val root = div {
            input = input(
                value = "ab",
                style = UiStyle(width = 40f.px, height = 10f.px),
                onInput = inputs::add,
                onChange = changes::add,
            )
        }
        val result = layout(root)

        assertTrue(
            result.mouseClick(MouseButtonEvent(6.0, 5.0, MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0))),
        )
        assertSame(input, result.focusedElement)
        assertEquals(1, input.caretPosition)
        assertTrue(result.charTyped(CharacterEvent('X'.code)))
        assertEquals("aXb", input.value)
        assertEquals(listOf("aXb"), inputs)

        result.clearFocus()

        assertNull(result.focusedElement)
        assertEquals(listOf("aXb"), changes)
    }

    @Test
    fun `programmatic value assignment dispatches neither input nor deferred change`() {
        val inputs = mutableListOf<String>()
        val changes = mutableListOf<String>()
        val input = TextInput("before", onInput = inputs::add, onChange = changes::add)
        val result = layout(input)
        result.focus(input)

        input.value = "after"
        result.clearFocus()

        assertTrue(inputs.isEmpty())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `keyboard navigation and deletion operate on Unicode code points`() {
        val input = TextInput("a😀b")
        val result = layout(input)
        assertTrue(result.focus(input))

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_LEFT, 0, 0)))
        assertEquals(3, input.caretPosition)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0)))

        assertEquals("ab", input.value)
        assertEquals(1, input.caretPosition)
    }

    @Test
    fun `preedit is transient and committed input clears it`() {
        val input = TextInput("a")
        val result = layout(input)
        result.focus(input)

        assertTrue(result.preeditUpdated(PreeditEvent("かな", 1, listOf("かな"), 0)))
        assertEquals("a", input.value)
        assertEquals(TextInputPreedit("かな", 1), input.preedit)

        assertTrue(result.charTyped(CharacterEvent('か'.code)))
        assertEquals("aか", input.value)
        assertNull(input.preedit)
    }

    @Test
    fun `read only input consumes text editing without changing value`() {
        val input = TextInput("locked", readOnly = true)
        val result = layout(input)
        result.focus(input)

        assertTrue(result.charTyped(CharacterEvent('x'.code)))
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0)))

        assertEquals("locked", input.value)
    }

    @Test
    fun `display none and disabled inputs lose focus`() {
        var hidden = false
        lateinit var input: TextInput
        val root = div {
            input = input(style = {
                UiStyle(display = if (hidden) UiDisplay.NONE else UiDisplay.INLINE)
            })
        }
        val result = layout(root)
        result.focus(input)

        hidden = true
        result.size

        assertNull(result.focusedElement)
        assertFalse(input.focused)

        hidden = false
        result.size
        input.disabled = false
        result.focus(input)
        input.disabled = true
        assertFalse(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_LEFT, 0, 0)))
        assertNull(result.focusedElement)
    }

    @Test
    fun `direct tab dispatch cycles through text inputs`() {
        lateinit var first: TextInput
        lateinit var second: TextInput
        val root = div {
            first = input()
            second = input()
        }
        val result = layout(root)
        result.focus(first)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0)))
        assertSame(second, result.focusedElement)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0)))
        assertSame(first, result.focusedElement)
    }

    @Test
    fun `screen focus path moves focus between inputs and toggles platform text input`() {
        lateinit var first: TextInput
        lateinit var second: TextInput
        val root = div {
            first = input()
            second = input()
        }
        val result = layout(root)
        val platformFocus = mutableListOf<Boolean>()
        result.textInputFocusNotifier = platformFocus::add

        val firstPath = result.nextFocusPath(FocusNavigationEvent.TabNavigation(true))!!
        firstPath.applyFocus(true)
        assertSame(first, result.focusedElement)

        val secondPath = result.nextFocusPath(FocusNavigationEvent.TabNavigation(true))!!
        firstPath.applyFocus(false)
        secondPath.applyFocus(true)

        assertSame(second, result.focusedElement)
        assertEquals(listOf(true, false, true), platformFocus)
    }
}
