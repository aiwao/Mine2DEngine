package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UiElementKeyboardFocusTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight: Float = 10f

        override fun width(text: String): Float = text.length * 5f
    }

    private fun layout(root: UiElement): UiLayout = calculateLayout(
        root = root,
        viewport = UiRect(0f, 0f, 100f, 100f),
        textMeasurer = textMeasurer,
    )

    @Test
    fun `focusable element receives pointer focus and observes keys without consuming them`() {
        val events = mutableListOf<String>()
        lateinit var target: Div
        val root = div {
            target = div(
                style = UiStyle(width = 20f.px, height = 20f.px),
                tabIndex = 0,
                onFocus = { events += "focus" },
                onBlur = { events += "blur" },
                onKeyPressed = { event -> events += "key:${event.key()}" },
            )
        }
        val result = layout(root)

        assertTrue(
            result.mouseClick(MouseButtonEvent(5.0, 5.0, MouseButtonInfo(0, 0))),
        )
        assertSame(target, result.focusedElement)
        assertTrue(target.focused)
        assertFalse(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)))

        assertFalse(
            result.mouseClick(MouseButtonEvent(50.0, 50.0, MouseButtonInfo(0, 0))),
        )
        assertNull(result.focusedElement)
        assertFalse(target.focused)
        assertEquals(listOf("focus", "key:${GLFW.GLFW_KEY_ENTER}", "blur"), events)
    }

    @Test
    fun `tab navigation orders positive indices before natural order and skips minus one`() {
        lateinit var natural: Div
        lateinit var second: Div
        lateinit var first: Div
        lateinit var pointerOnly: Div
        val root = div {
            natural = div(tabIndex = 0)
            second = div(tabIndex = 2)
            first = div(tabIndex = 1)
            pointerOnly = div(tabIndex = -1)
        }
        val result = layout(root)
        val tab = KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0)

        assertTrue(result.keyPressed(tab))
        assertSame(first, result.focusedElement)
        assertTrue(result.keyPressed(tab))
        assertSame(second, result.focusedElement)
        assertTrue(result.keyPressed(tab))
        assertSame(natural, result.focusedElement)
        assertTrue(result.keyPressed(tab))
        assertSame(first, result.focusedElement)

        assertTrue(result.focus(pointerOnly))
        assertSame(pointerOnly, result.focusedElement)
    }

    @Test
    fun `null and invalid tab indices cannot receive focus`() {
        val target = Div()
        val result = layout(target)

        assertFalse(result.focus(target))
        assertFailsWith<IllegalArgumentException> { target.tabIndex = -2 }
        assertFailsWith<IllegalArgumentException> { Div(tabIndex = -2) }
    }
}
