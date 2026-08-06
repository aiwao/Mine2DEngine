package io.github.aiwao.mine2dengine.layout

import com.mojang.blaze3d.font.GlyphProvider
import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DFont
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.GlyphStitcher
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `click invokes the topmost button and reports misses`() {
        var clickCount = 0
        lateinit var button: Button
        val root = div {
            button = button("Run") { clickCount++ }
        }
        val layout = calculateLayout(root, left = 4f, top = 6f, textMeasurer)

        assertSame(button, layout.elementAt(5f, 7f))
        assertTrue(layout.click(5f, 7f))
        assertEquals(1, clickCount)
        assertFalse(layout.click(100f, 100f))
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
