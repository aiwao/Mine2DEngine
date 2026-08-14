package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RangeInputTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight: Float = 10f
        override val baselineFromLineTop: Float = 7f

        override fun width(text: String): Float = text.codePointCount(0, text.length) * 5f
    }

    private fun layout(
        root: UiElement,
        width: Float = 180f,
        height: Float = 160f,
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
    fun `defaults and explicit values are clamped and aligned to step`() {
        assertEquals(50.0, RangeInput().value)
        assertEquals(60.0, RangeInput(value = null, min = 0.0, max = 100.0, step = 20.0).value)
        assertEquals(60.0, RangeInput(value = 50.0, min = 0.0, max = 100.0, step = 20.0).value)
        assertEquals(9.0, RangeInput(value = 100.0, min = 0.0, max = 10.0, step = 3.0).value)
        assertEquals(0.3, RangeInput(value = 0.3, min = 0.0, max = 1.0, step = 0.1).value)
    }

    @Test
    fun `range configuration rejects non-finite and reversed constraints`() {
        assertFailsWith<IllegalArgumentException> { RangeInput(min = 2.0, max = 1.0) }
        assertFailsWith<IllegalArgumentException> { RangeInput(value = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { RangeInput(step = 0.0) }
        assertFailsWith<IllegalArgumentException> { RangeInput(step = Double.POSITIVE_INFINITY) }

        val input = RangeInput()
        assertFailsWith<IllegalArgumentException> { input.min = 101.0 }
        assertFailsWith<IllegalArgumentException> { input.max = -1.0 }
        assertFailsWith<IllegalArgumentException> { input.value = Double.NEGATIVE_INFINITY }
    }

    @Test
    fun `programmatic changes sanitize silently and reset an active edit`() {
        val inputs = mutableListOf<Double>()
        val changes = mutableListOf<Double>()
        val input = RangeInput(
            value = 0.0,
            min = 0.0,
            max = 10.0,
            onInput = inputs::add,
            onChange = changes::add,
        )
        val result = layout(input)
        result.focus(input)

        input.beginUserEdit()
        input.setFromUser(3.0)
        input.value = 7.6
        input.max = 5.0
        input.commitUserEdit()

        assertEquals(5.0, input.value)
        assertEquals(listOf(3.0), inputs)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `horizontal pointer drag emits input changes and one committed change`() {
        val inputs = mutableListOf<Double>()
        val changes = mutableListOf<Double>()
        lateinit var input: RangeInput
        val root = div {
            input = rangeInput(
                value = 0.0,
                min = 0.0,
                max = 10.0,
                step = 1.0,
                onInput = inputs::add,
                onChange = changes::add,
            )
        }
        val result = layout(root)

        assertTrue(result.mouseClick(leftClick(14f, 10f)))
        assertSame(input, result.focusedElement)
        assertEquals(1.0, input.value)

        assertTrue(result.mouseMove(200.0, 10.0))
        assertEquals(10.0, input.value)
        assertTrue(result.mouseRelease())

        assertEquals(listOf(1.0, 10.0), inputs)
        assertEquals(listOf(10.0), changes)
        assertFalse(input.dragging)
    }

    @Test
    fun `focus loss commits an active pointer edit without duplicating release`() {
        val changes = mutableListOf<Double>()
        lateinit var input: RangeInput
        val root = div {
            input = rangeInput(
                value = 0.0,
                min = 0.0,
                max = 10.0,
                onChange = changes::add,
            )
        }
        val result = layout(root)

        result.mouseClick(leftClick(50f, 10f))
        result.clearFocus()
        result.mouseRelease()

        assertEquals(listOf(5.0), changes)
    }

    @Test
    fun `keyboard operations are atomic input and change transactions`() {
        val inputs = mutableListOf<Double>()
        val changes = mutableListOf<Double>()
        val input = RangeInput(
            value = 4.0,
            min = 0.0,
            max = 20.0,
            step = 2.0,
            onInput = inputs::add,
            onChange = changes::add,
        )
        val result = layout(input)
        result.focus(input)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0)))
        assertEquals(6.0, input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_PAGE_UP, 0, 0)))
        assertEquals(20.0, input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_HOME, 0, 0)))
        assertEquals(0.0, input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_END, 0, 0)))
        assertEquals(20.0, input.value)

        assertEquals(listOf(6.0, 20.0, 0.0, 20.0), inputs)
        assertEquals(inputs, changes)
    }

    @Test
    fun `step any keeps pointer values continuous and gives keyboard a range-relative step`() {
        val input = RangeInput(value = 25.0, min = 0.0, max = 100.0, step = null)
        val result = layout(input)
        result.focus(input)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_UP, 0, 0)))

        assertEquals(26.0, input.value)
    }

    @Test
    fun `vertical geometry increases from bottom to top`() {
        val input = RangeInput(
            value = 5.0,
            min = 0.0,
            max = 10.0,
            orientation = RangeOrientation.VERTICAL,
        )
        val bounds = UiRect(0f, 0f, 20f, 100f)
        val geometry = rangeInputGeometry(input, bounds)

        assertEquals(10f, geometry.thumbCenterX)
        assertEquals(50f, geometry.thumbCenterY)
        assertEquals(10.0, rangeInputValueAt(input, bounds, 10f, 5f))
        assertEquals(0.0, rangeInputValueAt(input, bounds, 10f, 95f))
    }

    @Test
    fun `range input is a font-independent replaced element in either orientation`() {
        val horizontal = rangeInput(style = UiStyle(width = UiSizeValue.MIN_CONTENT))
        val vertical = rangeInput(
            orientation = RangeOrientation.VERTICAL,
            style = UiStyle(width = UiSizeValue.MIN_CONTENT),
        )

        val horizontalResult = LayoutEngine.layout(horizontal, UiRect(0f, 0f, 180f, 160f))
        val verticalResult = LayoutEngine.layout(vertical, UiRect(0f, 0f, 180f, 160f))

        assertEquals(RangeInput.DEFAULT_LENGTH, horizontalResult.root.contentBounds.width)
        assertEquals(RangeInput.DEFAULT_THICKNESS, horizontalResult.root.contentBounds.height)
        assertEquals(RangeInput.DEFAULT_THICKNESS, verticalResult.root.contentBounds.width)
        assertEquals(RangeInput.DEFAULT_LENGTH, verticalResult.root.contentBounds.height)
    }

    @Test
    fun `tiny geometry keeps the thumb centered and pointer input stable`() {
        val input = RangeInput(value = 4.0, min = 0.0, max = 10.0)
        val bounds = UiRect(5f, 6f, 4f, 4f)
        val geometry = rangeInputGeometry(input, bounds)

        assertEquals(7f, geometry.thumbCenterX)
        assertEquals(8f, geometry.thumbCenterY)
        assertEquals(4.0, rangeInputValueAt(input, bounds, 100f, 100f))
    }

    @Test
    fun `disabled range consumes clicks without focus or mutation`() {
        lateinit var input: RangeInput
        val root = div {
            input = rangeInput(value = 2.0, min = 0.0, max = 10.0)
            input.disabled = true
        }
        val result = layout(root)

        assertTrue(result.mouseClick(leftClick(50f, 10f)))
        assertEquals(2.0, input.value)
        assertEquals(null, result.focusedElement)
    }

    @Test
    fun `range joins tab order without enabling platform text input`() {
        lateinit var text: TextInput
        lateinit var range: RangeInput
        val root = div {
            text = input()
            range = rangeInput()
        }
        val result = layout(root)
        val platformFocus = mutableListOf<Boolean>()
        result.textInputFocusNotifier = platformFocus::add

        result.setFocused(true)
        result.focus(text)
        result.focus(range)

        assertSame(range, result.focusedElement)
        assertEquals(listOf(true, false), platformFocus)
    }

    @Test
    fun `narration uses the accessible label and value formatter`() {
        val input = RangeInput(
            value = 0.25,
            min = 0.0,
            max = 1.0,
            step = 0.01,
            label = "Volume",
            valueText = { value -> "${(value * 100).toInt()} percent" },
        )

        assertEquals("Volume: 25 percent", input.narration())
    }
}
