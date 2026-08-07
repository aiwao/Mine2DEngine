package io.github.aiwao.mine2dengine.layout

import com.mojang.blaze3d.font.GlyphProvider
import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DFont
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.GlyphStitcher
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutEngineTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight = 10f

        override fun width(text: String): Float = text.length * 5f
    }

    @Test
    fun `layout engine selects fonts through style`() {
        assertNotNull(LayoutEngine::class.java.getConstructor(Mine2DEngine::class.java))
        assertNotNull(
            LayoutEngine::class.java.getMethod(
                "render",
                UiLayout::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ),
        )
        assertEquals(
            Mine2DFont::class.java,
            UiStyle::class.java.getMethod("getFont").returnType,
        )
    }

    @Test
    fun `text shadow is enabled by default and configurable through style`() {
        assertTrue(UiStyle().dropShadow)
        assertFalse(UiStyle(dropShadow = false).dropShadow)
    }

    @Test
    fun `font is inherited and can be overridden by a child style`() {
        val inheritedFont = fontToken("inherited")
        val overriddenFont = fontToken("overridden")
        lateinit var inheritedParagraph: Paragraph
        lateinit var overriddenParagraph: Paragraph
        val root = div(UiStyle(font = inheritedFont)) {
            inheritedParagraph = p("inherited")
            overriddenParagraph = p("overridden", UiStyle(font = overriddenFont))
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertSame(inheritedFont, layout.root.font)
        assertSame(inheritedFont, layout.nodeOf(inheritedParagraph)!!.font)
        assertSame(overriddenFont, layout.nodeOf(overriddenParagraph)!!.font)
    }

    @Test
    fun `vertical layout uses root origin box model and center alignment`() {
        lateinit var paragraph: Paragraph
        val root = div(
            UiStyle(
                padding = UiEdges(10f),
                width = 100f,
                direction = UiDirection.VERTICAL,
                alignment = UiAlignment.CENTER,
            ),
        ) {
            paragraph = p(
                "ab",
                UiStyle(margin = UiEdges(top = 1f, right = 2f, bottom = 3f, left = 4f)),
            )
        }

        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)
        val paragraphNode = layout.nodeOf(paragraph)!!

        assertEquals(UiRect(5f, 7f, 120f, 34f), layout.root.bounds)
        assertEquals(UiRect(57f, 17f, 16f, 14f), paragraphNode.outerBounds)
        assertEquals(UiRect(61f, 18f, 10f, 10f), paragraphNode.bounds)
    }

    @Test
    fun `horizontal layout aligns the whole row to the right`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                width = 100f,
                direction = UiDirection.HORIZONTAL,
                alignment = UiAlignment.RIGHT,
            ),
        ) {
            first = p("a", UiStyle(width = 20f))
            second = p("b", UiStyle(width = 30f))
        }

        val layout = calculateLayout(root, left = 10f, top = 20f, textMeasurer)

        assertEquals(60f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(80f, layout.nodeOf(second)!!.outerBounds.left)
        assertEquals(20f, layout.nodeOf(first)!!.outerBounds.top)
        assertEquals(20f, layout.nodeOf(second)!!.outerBounds.top)
    }

    @Test
    fun `vertical gap separates children and contributes to automatic size`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        lateinit var third: Paragraph
        val root = div(UiStyle(gap = 4f)) {
            first = p("a")
            second = p("b", UiStyle(margin = UiEdges(vertical = 1f, horizontal = 0f)))
            third = p("c")
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)

        assertEquals(UiSize(5f, 40f), layout.size)
        assertEquals(3f, layout.nodeOf(first)!!.outerBounds.top)
        assertEquals(17f, layout.nodeOf(second)!!.outerBounds.top)
        assertEquals(33f, layout.nodeOf(third)!!.outerBounds.top)
    }

    @Test
    fun `horizontal gap is included when aligning the whole row`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                width = 100f,
                direction = UiDirection.HORIZONTAL,
                alignment = UiAlignment.RIGHT,
                gap = 7f,
            ),
        ) {
            first = p("a", UiStyle(width = 20f))
            second = p("b", UiStyle(width = 30f))
        }

        val layout = calculateLayout(root, left = 10f, top = 20f, textMeasurer)

        assertEquals(53f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(80f, layout.nodeOf(second)!!.outerBounds.left)
    }

    @Test
    fun `changing layout origin translates every box without changing its size`() {
        lateinit var paragraph: Paragraph
        val root = div(
            UiStyle(
                margin = UiEdges(2f),
                padding = UiEdges(3f),
            ),
        ) {
            paragraph = p("move", UiStyle(margin = UiEdges(1f)))
        }
        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)
        val originalSize = layout.size

        layout.left = 25f
        layout.top = 37f

        assertEquals(25f, layout.left)
        assertEquals(37f, layout.top)
        assertEquals(originalSize, layout.size)
        assertEquals(UiRect(25f, 37f, 32f, 22f), layout.root.outerBounds)
        assertEquals(UiRect(27f, 39f, 28f, 18f), layout.root.bounds)
        assertEquals(UiRect(30f, 42f, 22f, 12f), layout.root.contentBounds)
        assertEquals(UiRect(30f, 42f, 22f, 12f), layout.nodeOf(paragraph)!!.outerBounds)
        assertEquals(UiRect(31f, 43f, 20f, 10f), layout.nodeOf(paragraph)!!.bounds)
        assertSame(paragraph, layout.elementAt(31f, 43f))
        assertEquals(null, layout.elementAt(11f, 13f))
    }

    @Test
    fun `layout origin must remain finite`() {
        val layout = calculateLayout(div(), left = 5f, top = 7f, textMeasurer)

        assertFailsWith<IllegalArgumentException> { layout.left = Float.NaN }
        assertFailsWith<IllegalArgumentException> { layout.top = Float.POSITIVE_INFINITY }
        assertEquals(5f, layout.left)
        assertEquals(7f, layout.top)
    }

    @Test
    fun `gap must be finite and non-negative`() {
        assertFailsWith<IllegalArgumentException> { UiStyle(gap = -1f) }
        assertFailsWith<IllegalArgumentException> { UiStyle(gap = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiStyle(gap = Float.POSITIVE_INFINITY) }
    }

    @Test
    fun `automatic size includes multiline text children margins and padding`() {
        val root = div(
            UiStyle(
                padding = UiEdges(vertical = 2f, horizontal = 3f),
                direction = UiDirection.HORIZONTAL,
            ),
        ) {
            p("abcd\nx", UiStyle(margin = UiEdges(1f)))
            p("xy")
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiSize(38f, 26f), layout.size)
        assertEquals(UiRect(3f, 2f, 32f, 22f), layout.root.contentBounds)
    }

    @Test
    fun `click invokes the topmost button with the mouse event and reports misses`() {
        var clickCount = 0
        var receivedEvent: MouseButtonEvent? = null
        lateinit var button: Button
        val root = div {
            button = button(onClick = { event ->
                clickCount++
                receivedEvent = event
            }) {
                p("Run")
            }
        }
        val layout = calculateLayout(root, left = 4f, top = 6f, textMeasurer)
        val hitEvent = MouseButtonEvent(5.0, 7.0, MouseButtonInfo(1, 2))
        val missEvent = MouseButtonEvent(100.0, 100.0, MouseButtonInfo(0, 0))

        assertSame(button, layout.elementAt(5f, 7f))
        assertTrue(layout.click(hitEvent))
        assertEquals(1, clickCount)
        assertSame(hitEvent, receivedEvent)
        assertTrue(button.dragging)
        assertFalse(layout.click(missEvent))
        assertTrue(button.dragging)
        assertTrue(layout.release())
        assertFalse(button.dragging)
    }

    @Test
    fun `onClick can be used by every element type`() {
        val clicked = mutableListOf<UiElement>()
        lateinit var paragraph: Paragraph
        lateinit var innerDiv: Div
        lateinit var root: Div
        root = div(
            style = UiStyle(width = 50f, height = 50f),
            onClick = { clicked += root },
        ) {
            innerDiv = div(
                style = UiStyle(width = 30f, height = 30f),
                onClick = { clicked += innerDiv },
            ) {
                paragraph = p(
                    text = "text",
                    onClick = { clicked += paragraph },
                )
            }
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.click(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertEquals(listOf<UiElement>(paragraph), clicked)

        clicked.clear()
        paragraph.onClick = null
        assertTrue(layout.click(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertEquals(listOf<UiElement>(innerDiv), clicked)

        clicked.clear()
        innerDiv.onClick = null
        assertTrue(layout.click(MouseButtonEvent(40.0, 40.0, MouseButtonInfo(0, 0))))
        assertEquals(listOf<UiElement>(root), clicked)
    }

    @Test
    fun `button without onClick still consumes a click`() {
        val root = div {
            button {
                p("Run")
            }
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.click(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
    }

    @Test
    fun `onMouseMove can be used by every element type`() {
        val moved = mutableListOf<UiElement>()
        var receivedCoordinates: Pair<Double, Double>? = null
        lateinit var paragraph: Paragraph
        lateinit var innerDiv: Div
        lateinit var button: Button
        lateinit var root: Div
        root = div(
            style = UiStyle(width = 50f, height = 50f),
            onMouseMove = { x, y ->
                moved += root
                receivedCoordinates = x to y
            },
        ) {
            innerDiv = div(
                style = UiStyle(width = 30f, height = 30f),
                onMouseMove = { _, _ -> moved += innerDiv },
            ) {
                paragraph = p(
                    text = "text",
                    onMouseMove = { x, y ->
                        moved += paragraph
                        receivedCoordinates = x to y
                    },
                )
            }
            button = button(onMouseMove = { _, _ -> moved += button }) {
                p("Button")
            }
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.mouseMove(1.25, 2.5))
        assertEquals(listOf<UiElement>(paragraph), moved)
        assertEquals(1.25 to 2.5, receivedCoordinates)

        moved.clear()
        paragraph.onMouseMove = null
        assertTrue(layout.mouseMove(1.25, 2.5))
        assertEquals(listOf<UiElement>(innerDiv), moved)

        moved.clear()
        innerDiv.onMouseMove = null
        assertTrue(layout.mouseMove(1.0, 31.0))
        assertEquals(listOf<UiElement>(button), moved)

        moved.clear()
        assertTrue(layout.mouseMove(49.0, 49.0))
        assertEquals(listOf<UiElement>(root), moved)

        root.onMouseMove = null
        assertFalse(layout.mouseMove(100.0, 100.0))
    }

    @Test
    fun `drag starts on click moves outside bounds and stops on release`() {
        val dragCoordinates = mutableListOf<Pair<Double, Double>>()
        lateinit var paragraph: Paragraph
        val root = div(UiStyle(width = 50f, height = 50f)) {
            paragraph = p(
                text = "Drag",
                onDrag = { x, y -> dragCoordinates += x to y },
            )
        }
        val layout = calculateLayout(root, left = 4f, top = 6f, textMeasurer)

        assertFalse(paragraph.dragging)
        assertTrue(layout.click(MouseButtonEvent(5.0, 7.0, MouseButtonInfo(0, 0))))
        assertTrue(paragraph.dragging)

        assertTrue(layout.mouseMove(100.25, 200.5))
        assertEquals(listOf(100.25 to 200.5), dragCoordinates)

        assertTrue(layout.release())
        assertFalse(paragraph.dragging)
        assertFalse(layout.release())
        assertFalse(layout.mouseMove(100.25, 200.5))
        assertEquals(listOf(100.25 to 200.5), dragCoordinates)
    }

    @Test
    fun `mouse move invokes both hover and drag callbacks while dragging`() {
        val callbacks = mutableListOf<String>()
        val root = div(
            style = UiStyle(width = 20f, height = 20f),
            onMouseMove = { _, _ -> callbacks += "move" },
            onDrag = { _, _ -> callbacks += "drag" },
        )
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.click(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertTrue(layout.mouseMove(2.0, 3.0))
        assertEquals(listOf("move", "drag"), callbacks)
    }

    @Test
    fun `mouse over and out update hovering state once per boundary crossing`() {
        val callbacks = mutableListOf<String>()
        lateinit var paragraph: Paragraph
        lateinit var root: Div
        root = div(
            style = UiStyle(width = 50f, height = 50f),
            onMouseOver = {
                assertFalse(root.hovering)
                callbacks += "root over"
            },
            onMouseOut = {
                assertTrue(root.hovering)
                callbacks += "root out"
            },
        ) {
            paragraph = p(
                text = "text",
                onMouseOver = {
                    assertFalse(paragraph.hovering)
                    callbacks += "paragraph over"
                },
                onMouseOut = {
                    assertTrue(paragraph.hovering)
                    callbacks += "paragraph out"
                },
            )
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.mouseMove(1.0, 1.0))
        assertTrue(root.hovering)
        assertTrue(paragraph.hovering)
        assertEquals(listOf("root over", "paragraph over"), callbacks)

        assertFalse(layout.mouseMove(2.0, 2.0))
        assertEquals(listOf("root over", "paragraph over"), callbacks)

        assertTrue(layout.mouseMove(30.0, 30.0))
        assertTrue(root.hovering)
        assertFalse(paragraph.hovering)
        assertEquals(listOf("root over", "paragraph over", "paragraph out"), callbacks)

        assertTrue(layout.mouseMove(100.0, 100.0))
        assertFalse(root.hovering)
        assertFalse(paragraph.hovering)
        assertEquals(
            listOf("root over", "paragraph over", "paragraph out", "root out"),
            callbacks,
        )
    }

    @Test
    fun `hovering updates even without mouse over or out callbacks`() {
        lateinit var paragraph: Paragraph
        val root = div(UiStyle(width = 20f, height = 20f)) {
            paragraph = p("text")
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertFalse(layout.mouseMove(1.0, 1.0))
        assertTrue(root.hovering)
        assertTrue(paragraph.hovering)

        assertFalse(layout.mouseMove(100.0, 100.0))
        assertFalse(root.hovering)
        assertFalse(paragraph.hovering)
    }

    @Test
    fun `button lays out child elements like a div`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div {
            button(
                style = Button.DEFAULT_STYLE.copy(
                    direction = UiDirection.HORIZONTAL,
                    gap = 2f,
                ),
            ) {
                first = p("a")
                second = p("bc")
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiSize(29f, 16f), layout.size)
        assertEquals(6f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(13f, layout.nodeOf(second)!!.outerBounds.left)
    }

    @Test
    fun `trailing newline creates an empty final line`() {
        val paragraph = Paragraph("a\n")
        val layout = calculateLayout(paragraph, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiSize(5f, 20f), layout.size)
    }

    private fun fontToken(path: String): Mine2DFont {
        val location = Identifier.fromNamespaceAndPath("test", "$path.ttf")
        val stitcher = GlyphStitcher::class.java
            .getConstructor(TextureManager::class.java, Identifier::class.java)
            .newInstance(null, location)
        val fontSet = FontSet(stitcher)
        val glyphProvider = object : GlyphProvider {
            override fun getSupportedGlyphs() = IntOpenHashSet()
        }
        val constructor = Mine2DFont::class.java.getDeclaredConstructor(
            Identifier::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            GlyphProvider::class.java,
            FontSet::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(location, 11f, 1f, glyphProvider, fontSet)
    }
}
