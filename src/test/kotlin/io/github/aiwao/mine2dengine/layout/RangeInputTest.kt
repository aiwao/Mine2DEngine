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
    fun `Double remains the default and aligns midpoint ties upward`() {
        val defaultInput: RangeInput<Double> = rangeInput()

        assertEquals(50.0, defaultInput.value)
        assertEquals(60.0, rangeInput(value = null, min = 0.0, max = 100.0, step = 20.0).value)
        assertEquals(60.0, rangeInput(value = 50.0, min = 0.0, max = 100.0, step = 20.0).value)
        assertEquals(9.0, rangeInput(value = 100.0, min = 0.0, max = 10.0, step = 3.0).value)
        assertEquals(0.3, rangeInput(value = 0.3, min = 0.0, max = 1.0, step = 0.1).value)
    }

    @Test
    fun `reified and inferred factories retain their numeric types`() {
        val explicitInt: RangeInput<Int> = rangeInput<Int>()
        val inferredInt: RangeInput<Int> = rangeInput(value = 3, min = 0, max = 10, step = 2)
        val tokenInt: RangeInput<Int> = rangeInput(RangeNumberTypes.INT, value = 7)
        val explicitFloat: RangeInput<Float> = rangeInput<Float>(
            value = 0.25f,
            min = 0f,
            max = 1f,
            step = 0.05f,
        )

        assertSame(RangeNumberTypes.INT, explicitInt.numberType)
        assertEquals(4, inferredInt.value)
        assertEquals(7, tokenInt.value)
        assertSame(RangeNumberTypes.FLOAT, explicitFloat.numberType)
        assertEquals(0.25f, explicitFloat.value)
    }

    @Test
    fun `Int arithmetic remains exact across its complete range`() {
        val fullRange = rangeInput<Int>(
            min = Int.MIN_VALUE,
            max = Int.MAX_VALUE,
            step = 1,
        )
        val tie = rangeInput<Int>(value = 50, min = 0, max = 100, step = 20)
        val negativeContinuous = rangeInput<Int>(
            value = -10,
            min = -10,
            max = -1,
            step = null,
        )

        assertEquals(0, fullRange.value)
        fullRange.value = Int.MAX_VALUE
        assertEquals(1.0, fullRange.fraction())
        assertEquals(60, tie.value)

        negativeContinuous.setFromUserFraction(0.5)
        assertEquals(-5, negativeContinuous.value)
    }

    @Test
    fun `Float step alignment returns Float values and callbacks`() {
        val values = mutableListOf<Float>()
        val input = rangeInput<Float>(
            value = 0.3f,
            min = 0f,
            max = 1f,
            step = 0.1f,
            onInput = values::add,
        )

        input.setFromUserFraction(0.55)

        assertEquals(0.6f, input.value)
        assertEquals(listOf(0.6f), values)
    }

    @Test
    fun `unsupported reified number types fail with a clear error`() {
        val error = assertFailsWith<IllegalArgumentException> { rangeInput<Long>() }

        assertTrue(error.message.orEmpty().contains("Int, Float, or Double"))
    }

    @Test
    fun `range configuration rejects non-finite and reversed constraints`() {
        assertFailsWith<IllegalArgumentException> { rangeInput(min = 2.0, max = 1.0) }
        assertFailsWith<IllegalArgumentException> { rangeInput(value = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { rangeInput(step = 0.0) }
        assertFailsWith<IllegalArgumentException> { rangeInput(step = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { rangeInput<Int>(step = 0) }

        val input = rangeInput()
        assertFailsWith<IllegalArgumentException> { input.min = 101.0 }
        assertFailsWith<IllegalArgumentException> { input.max = -1.0 }
        assertFailsWith<IllegalArgumentException> { input.value = Double.NEGATIVE_INFINITY }
    }

    @Test
    fun `programmatic changes sanitize silently and reset an active edit`() {
        val inputs = mutableListOf<Double>()
        val changes = mutableListOf<Double>()
        val input = rangeInput(
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
    fun `horizontal pointer drag preserves Int callback types and commits once`() {
        val inputs = mutableListOf<Int>()
        val changes = mutableListOf<Int>()
        lateinit var input: RangeInput<Int>
        val root = div {
            input = rangeInput<Int>(
                value = 0,
                min = 0,
                max = 10,
                step = 1,
                style = { range ->
                    UiStyle(backgroundColor = if (range.focused) 0xFF202020.toInt() else null)
                },
                onInput = inputs::add,
                onChange = changes::add,
            )
        }
        val result = layout(root)

        assertTrue(result.mouseClick(leftClick(14f, 10f)))
        assertSame(input, result.focusedElement)
        assertEquals(1, input.value)

        assertTrue(result.mouseMove(200.0, 10.0))
        assertEquals(10, input.value)
        assertTrue(result.mouseRelease())

        assertEquals(listOf(1, 10), inputs)
        assertEquals(listOf(10), changes)
        assertFalse(input.dragging)
    }

    @Test
    fun `focus loss commits an active pointer edit without duplicating release`() {
        val changes = mutableListOf<Double>()
        lateinit var input: RangeInput<Double>
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
    fun `keyboard operations are atomic typed input and change transactions`() {
        val inputs = mutableListOf<Int>()
        val changes = mutableListOf<Int>()
        val input = rangeInput<Int>(
            value = 4,
            min = 0,
            max = 20,
            step = 2,
            onInput = inputs::add,
            onChange = changes::add,
        )
        val result = layout(input)
        result.focus(input)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0)))
        assertEquals(6, input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_PAGE_UP, 0, 0)))
        assertEquals(20, input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_HOME, 0, 0)))
        assertEquals(0, input.value)
        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_END, 0, 0)))
        assertEquals(20, input.value)

        assertEquals(listOf(6, 20, 0, 20), inputs)
        assertEquals(inputs, changes)
    }

    @Test
    fun `step any keeps pointer values continuous and gives keyboard a range-relative step`() {
        val input = rangeInput(value = 25.0, min = 0.0, max = 100.0, step = null)
        val result = layout(input)
        result.focus(input)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_UP, 0, 0)))

        assertEquals(26.0, input.value)
    }

    @Test
    fun `Int step any keyboard movement is at least one`() {
        val input = rangeInput<Int>(value = 0, min = 0, max = 10, step = null)
        val result = layout(input)
        result.focus(input)

        assertTrue(result.keyPressed(KeyEvent(GLFW.GLFW_KEY_UP, 0, 0)))

        assertEquals(1, input.value)
    }

    @Test
    fun `vertical geometry exposes a bottom to top fraction`() {
        val input = rangeInput<Float>(
            value = 5f,
            min = 0f,
            max = 10f,
            orientation = RangeOrientation.VERTICAL,
        )
        val bounds = UiRect(0f, 0f, 20f, 100f)
        val geometry = rangeInputGeometry(input, bounds)

        assertEquals(10f, geometry.thumbCenterX)
        assertEquals(50f, geometry.thumbCenterY)
        assertEquals(1.0, rangeInputFractionAt(input, bounds, 10f, 5f))
        assertEquals(0.0, rangeInputFractionAt(input, bounds, 10f, 95f))
    }

    @Test
    fun `typed range inputs are font-independent replaced elements`() {
        val horizontal = rangeInput<Int>(style = UiStyle(width = UiSizeValue.MIN_CONTENT))
        val vertical = rangeInput<Float>(
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
    fun `tiny geometry keeps the thumb centered and pointer fraction stable`() {
        val input = rangeInput(value = 4.0, min = 0.0, max = 10.0)
        val bounds = UiRect(5f, 6f, 4f, 4f)
        val geometry = rangeInputGeometry(input, bounds)

        assertEquals(7f, geometry.thumbCenterX)
        assertEquals(8f, geometry.thumbCenterY)
        assertEquals(0.4, rangeInputFractionAt(input, bounds, 100f, 100f))
    }

    @Test
    fun `disabled typed range consumes clicks without focus or mutation`() {
        lateinit var input: RangeInput<Int>
        val root = div {
            input = rangeInput<Int>(value = 2, min = 0, max = 10)
            input.disabled = true
        }
        val result = layout(root)

        assertTrue(result.mouseClick(leftClick(50f, 10f)))
        assertEquals(2, input.value)
        assertEquals(null, result.focusedElement)
    }

    @Test
    fun `typed range joins tab order without enabling platform text input`() {
        lateinit var text: TextInput
        lateinit var range: RangeInput<Float>
        val root = div {
            text = input()
            range = rangeInput<Float>()
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
    fun `narration uses a typed accessible value formatter`() {
        val input = rangeInput<Int>(
            value = 25,
            min = 0,
            max = 100,
            label = "Volume",
            valueText = { value -> "$value percent" },
        )

        assertEquals("Volume: 25 percent", input.narration())
    }
}
