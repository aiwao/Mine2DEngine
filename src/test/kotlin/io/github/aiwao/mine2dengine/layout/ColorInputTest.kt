package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ColorInputTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight: Float = 10f
        override val baselineFromLineTop: Float = 7f

        override fun width(text: String): Float = text.codePointCount(0, text.length) * 5f
    }

    private fun layout(
        root: UiElement,
        width: Float = 180f,
        height: Float = 140f,
    ): UiLayout = calculateLayout(
        root = root,
        viewport = UiRect(0f, 0f, width, height),
        textMeasurer = textMeasurer,
    )

    private fun leftClick(x: Float, y: Float): MouseButtonEvent = MouseButtonEvent(
        x.toDouble(),
        y.toDouble(),
        MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0),
    )

    @Test
    fun `color values are opaque RGB and programmatic assignment is silent`() {
        val inputs = mutableListOf<Int>()
        val changes = mutableListOf<Int>()
        val input = ColorInput(
            value = 0x00123456,
            onInput = inputs::add,
            onChange = changes::add,
        )

        assertEquals(0xFF123456.toInt(), input.value)
        assertEquals("#123456", input.value.toHexRgb())

        input.value = 0x00789ABC

        assertEquals(0xFF789ABC.toInt(), input.value)
        assertTrue(inputs.isEmpty())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `HSV conversion preserves RGB primary colors`() {
        val red = 0xFFFF0000.toInt().toHsv()
        val green = 0xFF00FF00.toInt().toHsv()
        val blue = 0xFF0000FF.toInt().toHsv()

        assertEquals(ColorHsv(0f, 1f, 1f), red)
        assertEquals(ColorHsv(120f, 1f, 1f), green)
        assertEquals(ColorHsv(240f, 1f, 1f), blue)
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(red.hue, red.saturation, red.value))
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(green.hue, green.saturation, green.value))
        assertEquals(0xFF0000FF.toInt(), hsvToArgb(blue.hue, blue.saturation, blue.value))
    }

    @Test
    fun `color input is a replaced element with stable intrinsic dimensions`() {
        lateinit var input: ColorInput
        val root = div {
            input = colorInput(value = 0xFF336699.toInt())
        }

        val result = layout(root)
        val node = assertNotNull(result.nodeOf(input))

        assertEquals(
            UiDisplay.Box(UiDisplayOutside.INLINE, UiDisplayInside.FLOW_ROOT),
            node.styleProvider().display,
        )
        assertEquals(ColorInput.DEFAULT_WIDTH, node.contentBounds.width)
        assertEquals(ColorInput.DEFAULT_HEIGHT, node.contentBounds.height)

        input.value = 0xFFABCDEF.toInt()

        assertSame(node, result.nodeOf(input))
    }

    @Test
    fun `color input intrinsic metrics do not require a font`() {
        val input = colorInput(
            value = 0xFF336699.toInt(),
            style = UiStyle(width = UiSizeValue.MIN_CONTENT),
        )

        val result = LayoutEngine.layout(input, UiRect(0f, 0f, 100f, 100f))

        assertEquals(ColorInput.DEFAULT_WIDTH, result.root.contentBounds.width)
        assertEquals(ColorInput.DEFAULT_HEIGHT, result.root.contentBounds.height)
    }

    @Test
    fun `pointer picker emits input while editing and one change when dismissed`() {
        val inputs = mutableListOf<Int>()
        val changes = mutableListOf<Int>()
        lateinit var input: ColorInput
        val root = div {
            input = colorInput(
                value = 0xFFFF0000.toInt(),
                onInput = inputs::add,
                onChange = changes::add,
            )
        }
        val result = layout(root)

        assertTrue(result.mouseClick(leftClick(5f, 5f)))
        assertSame(input, result.focusedElement)
        assertTrue(input.pickerOpen)

        val picker = assertNotNull(result.colorPickerGeometry(input))
        val selectedX = picker.saturationValueBounds.left + picker.saturationValueBounds.width * 0.5f
        val selectedY = picker.saturationValueBounds.top + picker.saturationValueBounds.height * 0.5f
        assertTrue(result.mouseClick(leftClick(selectedX, selectedY)))
        assertTrue(result.mouseRelease())

        assertEquals(1, inputs.size)
        assertNotEquals(0xFFFF0000.toInt(), input.value)
        assertTrue(changes.isEmpty())

        assertFalse(result.mouseClick(leftClick(170f, 130f)))

        assertFalse(input.pickerOpen)
        assertEquals(listOf(input.value), changes)
    }

    @Test
    fun `escape restores picker opening value without dispatching change`() {
        val inputs = mutableListOf<Int>()
        val changes = mutableListOf<Int>()
        val input = ColorInput(
            value = 0xFF808080.toInt(),
            onInput = inputs::add,
            onChange = changes::add,
        )
        val result = layout(input)
        result.focus(input)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)))
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0)))
        assertNotEquals(0xFF808080.toInt(), input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)))

        assertEquals(0xFF808080.toInt(), input.value)
        assertEquals(2, inputs.size)
        assertTrue(changes.isEmpty())
        assertFalse(input.pickerOpen)
    }

    @Test
    fun `enter commits keyboard picker edit once`() {
        val changes = mutableListOf<Int>()
        val input = ColorInput(value = 0xFF808080.toInt(), onChange = changes::add)
        val result = layout(input)
        result.focus(input)

        result.keyPressed(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        result.keyPressed(KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0))
        result.keyPressed(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        result.clearFocus()

        assertEquals(listOf(input.value), changes)
    }

    @Test
    fun `tab order includes text and color controls without enabling IME for color`() {
        lateinit var text: TextInput
        lateinit var color: ColorInput
        val root = div {
            text = input()
            color = colorInput()
        }
        val result = layout(root)
        val platformFocus = mutableListOf<Boolean>()
        result.textInputFocusNotifier = platformFocus::add

        val textPath = assertNotNull(result.nextFocusPath(FocusNavigationEvent.TabNavigation(true)))
        textPath.applyFocus(true)
        assertSame(text, result.focusedElement)

        val colorPath = assertNotNull(result.nextFocusPath(FocusNavigationEvent.TabNavigation(true)))
        textPath.applyFocus(false)
        colorPath.applyFocus(true)

        assertSame(color, result.focusedElement)
        assertEquals(listOf(true, false), platformFocus)
    }
}
