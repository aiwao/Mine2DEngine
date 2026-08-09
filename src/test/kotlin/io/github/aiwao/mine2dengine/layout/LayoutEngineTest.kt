package io.github.aiwao.mine2dengine.layout

import com.mojang.blaze3d.font.GlyphProvider
import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DFont
import io.github.aiwao.mine2dengine.Mine2DFontMetrics
import io.github.aiwao.mine2dengine.Mine2DMaterial
import io.github.aiwao.mine2dengine.Mine2DMaterials
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.GlyphStitcher
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutEngineTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight = 10f

        override fun width(text: String): Float = text.length * 5f
    }

    @Test
    fun `elements expose their tag`() {
        lateinit var childDiv: Div
        lateinit var dynamicDiv: Div
        lateinit var shortParagraph: Paragraph
        lateinit var longParagraph: Paragraph
        lateinit var dynamicParagraph: Paragraph
        val root = div(tag = "main") {
            childDiv = div(tag = "section")
            dynamicDiv = div(style = { UiStyle() }, tag = "article")
            shortParagraph = p("p", tag = "label")
            longParagraph = paragraph("paragraph", tag = "strong")
            dynamicParagraph = p("dynamic", style = { UiStyle() }, tag = "span")
        }

        assertEquals("main", root.tag)
        assertEquals("section", childDiv.tag)
        assertEquals("article", dynamicDiv.tag)
        assertEquals("label", shortParagraph.tag)
        assertEquals("strong", longParagraph.tag)
        assertEquals("span", dynamicParagraph.tag)
        assertEquals("aside", Div(tag = "aside").tag)
        assertEquals("small", Paragraph("text", tag = "small").tag)
    }

    @Test
    fun `elements use their default tag`() {
        val root = div {
            assertEquals("div", div().tag)
            assertEquals("p", p("p").tag)
            assertEquals("p", paragraph("paragraph").tag)
        }

        assertEquals("div", root.tag)
        assertEquals("div", Div().tag)
        assertEquals("p", Paragraph("text").tag)
    }

    @Test
    fun `layout engine calculates and layouts render themselves`() {
        val layoutMethod = LayoutEngine::class.java.getMethod(
            "layout",
            UiElement::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        assertTrue(Modifier.isStatic(layoutMethod.modifiers))
        assertNotNull(
            UiLayout::class.java.getMethod(
                "render",
                Mine2DEngine::class.java,
            ),
        )
        assertNotNull(
            UiLayout::class.java.getMethod(
                "render",
                Mine2DEngine::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ),
        )
        assertEquals(
            Mine2DFont::class.java,
            UiStyle::class.java.getMethod("getFont").returnType,
        )
        assertEquals(
            Int::class.javaObjectType,
            UiStyle::class.java.getMethod("getBackgroundColor").returnType,
        )
        assertEquals(
            Mine2DMaterial::class.java,
            UiStyle::class.java.getMethod("getBackgroundMaterial").returnType,
        )
        assertEquals(
            UiBoxShadow::class.java,
            UiStyle::class.java.getMethod("getBoxShadow").returnType,
        )
        assertEquals(
            UiDropShadow::class.java,
            UiStyle::class.java.getMethod("getDropShadow").returnType,
        )
        assertEquals(
            UiTextShadow::class.java,
            UiStyle::class.java.getMethod("getTextShadow").returnType,
        )
        assertEquals(
            UiPosition::class.java,
            UiStyle::class.java.getMethod("getPosition").returnType,
        )
        assertEquals(
            UiLength::class.java,
            UiStyle::class.java.getMethod("getWidth").returnType,
        )
    }

    @Test
    fun `root text style uses visible defaults`() {
        val layout = calculateLayout(Paragraph("default"), left = 0f, top = 0f, textMeasurer)

        assertEquals(null, UiStyle().color)
        assertEquals(null, UiStyle().backgroundColor)
        assertEquals(null, UiStyle().backgroundMaterial)
        assertEquals(null, UiStyle().boxShadow)
        assertEquals(null, UiStyle().dropShadow)
        assertEquals(null, UiStyle().textShadow)
        assertEquals(UiBoxSizing.CONTENT_BOX, UiStyle().boxSizing)
        assertEquals(UiPosition.STATIC, UiStyle().position)
        assertNull(UiStyle().left)
        assertNull(UiStyle().top)
        assertNull(UiStyle().right)
        assertNull(UiStyle().bottom)
        assertFalse(UiStyle().noneDisplay())
        assertEquals(UiStyle.DEFAULT_COLOR, layout.root.color)
        assertNull(layout.root.textShadow)
    }

    @Test
    fun `null background color and material do not draw a background`() {
        val draws = backgroundDraws(UiStyle())

        assertTrue(draws.isEmpty())
    }

    @Test
    fun `background color uses the renderer material when no material is specified`() {
        val rendererMaterial = Mine2DMaterials.COLOR
        val draws = backgroundDraws(
            UiStyle(backgroundColor = 0xFF123456.toInt()),
            rendererMaterial,
        )

        assertEquals(1, draws.size)
        assertEquals(0xFF123456.toInt(), draws.single().first)
        assertSame(rendererMaterial, draws.single().second)
    }

    @Test
    fun `background material without a color does not draw a background`() {
        val draws = backgroundDraws(
            UiStyle(backgroundMaterial = Mine2DMaterials.COLOR.with {}),
        )

        assertTrue(draws.isEmpty())
    }

    @Test
    fun `background color and material draw with the specified material`() {
        val backgroundMaterial = Mine2DMaterials.COLOR.with {}
        val draws = backgroundDraws(
            UiStyle(
                backgroundColor = 0xFFABCDEF.toInt(),
                backgroundMaterial = backgroundMaterial,
            ),
        )

        assertEquals(1, draws.size)
        assertEquals(0xFFABCDEF.toInt(), draws.single().first)
        assertSame(backgroundMaterial, draws.single().second)
    }

    @Test
    fun `overriding only background color preserves the existing material`() {
        val backgroundMaterial = Mine2DMaterials.COLOR.with {}
        val base = UiStyle(
            backgroundColor = 0xFF000000.toInt(),
            backgroundMaterial = backgroundMaterial,
        )

        val result = base.withOverrides(UiStyle(backgroundColor = 0xFFFFFFFF.toInt()))

        assertEquals(0xFFFFFFFF.toInt(), result.backgroundColor)
        assertSame(backgroundMaterial, result.backgroundMaterial)
    }

    @Test
    fun `overriding only background material preserves the existing color`() {
        val originalMaterial = Mine2DMaterials.COLOR
        val replacementMaterial = originalMaterial.with {}
        val base = UiStyle(
            backgroundColor = 0xFF123456.toInt(),
            backgroundMaterial = originalMaterial,
        )

        val result = base.withOverrides(UiStyle(backgroundMaterial = replacementMaterial))

        assertEquals(0xFF123456.toInt(), result.backgroundColor)
        assertSame(replacementMaterial, result.backgroundMaterial)
    }

    @Test
    fun `all shadow styles use consistent offset and blur defaults`() {
        fun assertDefaults(offsetX: Float, offsetY: Float, blurRadius: Float) {
            assertEquals(0f, offsetX)
            assertEquals(0f, offsetY)
            assertEquals(4f, blurRadius)
        }

        UiBoxShadow().let { shadow ->
            assertDefaults(shadow.offsetX, shadow.offsetY, shadow.blurRadius)
        }
        UiDropShadow().let { shadow ->
            assertDefaults(shadow.offsetX, shadow.offsetY, shadow.blurRadius)
        }
        UiTextShadow().let { shadow ->
            assertDefaults(shadow.offsetX, shadow.offsetY, shadow.blurRadius)
        }
    }

    @Test
    fun `dynamic styles receive their elements and react to current state`() {
        val evaluations = mutableMapOf<String, Int>()
        fun evaluated(name: String) {
            evaluations[name] = evaluations.getOrDefault(name, 0) + 1
        }

        lateinit var innerDiv: Div
        lateinit var shortParagraph: Paragraph
        lateinit var longParagraph: Paragraph
        val root = div(
            style = { element ->
                evaluated("root")
                UiStyle(
                    color = if (element.hovering) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(),
                    width = 80f.px,
                    height = 80f,
                )
            },
        ) {
            innerDiv = div(style = { element ->
                evaluated("div")
                UiStyle(width = element.children.size.toFloat().px, height = 1f)
            })
            shortParagraph = p("p", style = { element ->
                evaluated("p")
                UiStyle(width = element.text.length.toFloat().px)
            })
            longParagraph = paragraph("paragraph", style = { element ->
                evaluated("paragraph")
                UiStyle(width = element.text.length.toFloat().px)
            })
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(
            mapOf("root" to 1, "div" to 1, "p" to 1, "paragraph" to 1),
            evaluations,
        )
        assertSame(innerDiv, root.children[0])
        assertSame(shortParagraph, root.children[1])
        assertSame(longParagraph, root.children[2])
        assertEquals(0xFF000000.toInt(), root.style.color)

        layout.mouseMove(79.0, 79.0)
        assertTrue(root.hovering)
        assertEquals(0xFFFFFFFF.toInt(), root.style.color)

        root.style = UiStyle(color = 0xFF123456.toInt())
        assertEquals(0xFF123456.toInt(), root.style.color)
        assertEquals(3, evaluations["root"])
    }

    @Test
    fun `dynamic styles can replace background color and material without relayout`() {
        val restingMaterial = Mine2DMaterials.COLOR
        val hoveringMaterial = restingMaterial.with {}
        val root = div(
            style = { element ->
                UiStyle(
                    width = 20f.px,
                    height = 20f,
                    backgroundColor = if (element.hovering) {
                        0xFFFFFFFF.toInt()
                    } else {
                        0xFF000000.toInt()
                    },
                    backgroundMaterial = if (element.hovering) {
                        hoveringMaterial
                    } else {
                        restingMaterial
                    },
                )
            },
        )
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(0xFF000000.toInt(), root.style.backgroundColor)
        assertSame(restingMaterial, root.style.backgroundMaterial)
        assertSame(root, layout.elementAt(10f, 10f))
        layout.mouseMove(10.0, 10.0)
        assertEquals(0xFFFFFFFF.toInt(), root.style.backgroundColor)
        assertSame(hoveringMaterial, root.style.backgroundMaterial)
        assertEquals(UiSize(20f, 20f), layout.size)
        assertSame(root, layout.elementAt(10f, 10f))
    }

    @Test
    fun `background properties do not affect layout size or pointer bounds`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f,
                backgroundColor = 0xFFFFFFFF.toInt(),
                backgroundMaterial = Mine2DMaterials.COLOR.with {},
            ),
        )
        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiSize(20f, 10f), layout.size)
        assertEquals(UiRect(5f, 7f, 20f, 10f), layout.root.bounds)
        assertSame(root, layout.elementAt(24f, 16f))
        assertNull(layout.elementAt(25f, 17f))
    }

    @Test
    fun `descendant style receives and dynamically styles every descendant`() {
        val outerEvaluations = mutableListOf<UiElement>()
        val nestedEvaluations = mutableListOf<UiElement>()
        val paragraphShadow = UiDropShadow()
        var shadowsEnabled = false
        val descendantStyle: (UiElement) -> UiStyle = { child ->
            outerEvaluations += child
            UiStyle(
                color = 0xFF112233.toInt(),
                width = 20f.px,
                height = 10f,
                dropShadow = paragraphShadow.takeIf { shadowsEnabled && child is Paragraph },
            )
        }
        val nestedDescendantStyle: (UiElement) -> UiStyle = { child ->
            nestedEvaluations += child
            UiStyle(
                color = 0xFF445566.toInt(),
                width = 8f.px,
            )
        }
        lateinit var directParagraph: Paragraph
        lateinit var nestedDiv: Div
        lateinit var nestedParagraph: Paragraph
        lateinit var styledDiv: Div
        lateinit var styledParagraph: Paragraph
        val root = div(
            style = UiStyle(width = 80f.px, height = 60f),
            descendantStyle = descendantStyle,
        ) {
            directParagraph = p("direct", UiStyle(width = 1f.px))
            nestedDiv = div {
                nestedParagraph = p("nested", UiStyle(width = 2f.px))
            }
            styledDiv = div(style = UiStyle(), descendantStyle = nestedDescendantStyle) {
                styledParagraph = p("styled")
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiRect(0f, 0f, 80f, 60f), layout.root.bounds)
        assertEquals(
            listOf(directParagraph, nestedDiv, nestedParagraph, styledDiv, styledParagraph),
            outerEvaluations,
        )
        assertEquals(listOf<UiElement>(styledParagraph), nestedEvaluations)
        val expectedSizes = mapOf(
            directParagraph to UiSize(1f, 10f),
            nestedDiv to UiSize(20f, 10f),
            nestedParagraph to UiSize(2f, 10f),
            styledDiv to UiSize(20f, 10f),
        )
        expectedSizes.forEach { (descendant, expectedSize) ->
            val node = layout.nodeOf(descendant)!!
            assertEquals(expectedSize, UiSize(node.bounds.width, node.bounds.height))
            assertEquals(0xFF112233.toInt(), node.color)
        }
        val styledParagraphNode = layout.nodeOf(styledParagraph)!!
        assertEquals(
            UiSize(8f, 10f),
            UiSize(styledParagraphNode.bounds.width, styledParagraphNode.bounds.height),
        )
        assertEquals(0xFF445566.toInt(), styledParagraphNode.color)
        assertEquals(1f.px, directParagraph.style.width)
        assertNull(layout.nodeOf(directParagraph)!!.styleProvider().dropShadow)

        shadowsEnabled = true
        assertSame(paragraphShadow, layout.nodeOf(directParagraph)!!.styleProvider().dropShadow)
    }

    @Test
    fun `child style receives and dynamically styles only direct children`() {
        val childEvaluations = mutableListOf<UiElement>()
        var directChildHeight = 10f
        val descendantBackground = 0xFF112233.toInt()
        val childBackground = 0xFF445566.toInt()
        lateinit var directParagraph: Paragraph
        lateinit var nestedDiv: Div
        lateinit var nestedParagraph: Paragraph
        val root = div(
            style = UiStyle(width = 80f.px, height = 60f),
            descendantStyle = {
                UiStyle(
                    width = 20f.px,
                    height = 8f,
                    backgroundColor = descendantBackground,
                )
            },
            childStyle = { child ->
                childEvaluations += child
                UiStyle(
                    width = 12f.px,
                    height = directChildHeight,
                    backgroundColor = childBackground,
                )
            },
        ) {
            directParagraph = p("direct", UiStyle(width = 1f.px))
            nestedDiv = div {
                nestedParagraph = p("nested")
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(listOf(directParagraph, nestedDiv), childEvaluations)
        val directParagraphStyle = layout.nodeOf(directParagraph)!!.styleProvider()
        assertEquals(1f.px, directParagraphStyle.width)
        assertEquals(10f, directParagraphStyle.height)
        assertEquals(childBackground, directParagraphStyle.backgroundColor)
        val nestedDivStyle = layout.nodeOf(nestedDiv)!!.styleProvider()
        assertEquals(12f.px, nestedDivStyle.width)
        assertEquals(10f, nestedDivStyle.height)
        assertEquals(childBackground, nestedDivStyle.backgroundColor)
        val nestedParagraphStyle = layout.nodeOf(nestedParagraph)!!.styleProvider()
        assertEquals(20f.px, nestedParagraphStyle.width)
        assertEquals(8f, nestedParagraphStyle.height)
        assertEquals(descendantBackground, nestedParagraphStyle.backgroundColor)

        directChildHeight = 14f
        assertEquals(14f, layout.nodeOf(nestedDiv)!!.styleProvider().height)
        assertEquals(8f, layout.nodeOf(nestedParagraph)!!.styleProvider().height)
    }

    @Test
    fun `box shadow does not affect layout or pointer bounds`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f,
                boxShadow = UiBoxShadow(
                    offsetX = 12f,
                    offsetY = 8f,
                    blurRadius = 6f,
                    spreadRadius = 4f,
                ),
            ),
        )
        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiSize(20f, 10f), layout.size)
        assertEquals(UiRect(5f, 7f, 20f, 10f), layout.root.bounds)
        assertSame(root, layout.elementAt(24f, 16f))
        assertNull(layout.elementAt(30f, 20f))
    }

    @Test
    fun `box shadow parameters reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { UiBoxShadow(offsetX = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiBoxShadow(offsetY = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { UiBoxShadow(blurRadius = -1f) }
        assertFailsWith<IllegalArgumentException> { UiBoxShadow(blurRadius = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiBoxShadow(spreadRadius = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiBoxShadow(cornerRadius = -1f) }
    }

    @Test
    fun `drop shadow does not affect layout or pointer bounds`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f,
                dropShadow = UiDropShadow(
                    offsetX = 12f,
                    offsetY = 8f,
                    blurRadius = 6f,
                ),
            ),
        )
        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiSize(20f, 10f), layout.size)
        assertEquals(UiRect(5f, 7f, 20f, 10f), layout.root.bounds)
        assertSame(root, layout.elementAt(24f, 16f))
        assertNull(layout.elementAt(30f, 20f))
    }

    @Test
    fun `drop shadow parameters reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { UiDropShadow(offsetX = Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            UiDropShadow(offsetY = Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { UiDropShadow(blurRadius = -1f) }
        assertFailsWith<IllegalArgumentException> { UiDropShadow(blurRadius = Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            UiDropShadow(offsetX = Float.MAX_VALUE, blurRadius = Float.MAX_VALUE)
        }
    }

    @Test
    fun `text shadow parameters reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { UiTextShadow(offsetX = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiTextShadow(offsetY = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { UiTextShadow(blurRadius = -1f) }
        assertFailsWith<IllegalArgumentException> { UiTextShadow(blurRadius = Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            UiTextShadow(offsetX = Float.MAX_VALUE, blurRadius = Float.MAX_VALUE)
        }
    }

    @Test
    fun `text shadow does not affect text layout or pointer bounds`() {
        val paragraph = Paragraph(
            "text",
            UiStyle(
                textShadow = UiTextShadow(
                    offsetX = 20f,
                    offsetY = 20f,
                    blurRadius = 10f,
                ),
            ),
        )
        val layout = calculateLayout(paragraph, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiSize(20f, 10f), layout.size)
        assertEquals(UiRect(5f, 7f, 20f, 10f), layout.root.bounds)
        assertSame(paragraph, layout.elementAt(24f, 16f))
        assertNull(layout.elementAt(30f, 20f))
    }

    @Test
    fun `text style is inherited and can be overridden by a child`() {
        val inheritedFont = fontToken("inherited")
        val overriddenFont = fontToken("overridden")
        val inheritedShadow = UiTextShadow(color = 0x80112233.toInt())
        val overriddenShadow = UiTextShadow(color = 0x80445566.toInt(), blurRadius = 2f)
        lateinit var inheritedParagraph: Paragraph
        lateinit var overriddenParagraph: Paragraph
        lateinit var shadowlessParagraph: Paragraph
        val root = div(
            UiStyle(
                color = 0xFF112233.toInt(),
                font = inheritedFont,
                textShadow = inheritedShadow,
            ),
        ) {
            div {
                inheritedParagraph = p("inherited", UiStyle(width = 20f.px))
                overriddenParagraph = p(
                    "overridden",
                    UiStyle(
                        color = 0xFF445566.toInt(),
                        font = overriddenFont,
                        textShadow = overriddenShadow,
                    ),
                )
                shadowlessParagraph = p(
                    "shadowless",
                    UiStyle(textShadow = UiTextShadow.NONE),
                )
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)
        val inheritedNode = layout.nodeOf(inheritedParagraph)!!
        val overriddenNode = layout.nodeOf(overriddenParagraph)!!
        val shadowlessNode = layout.nodeOf(shadowlessParagraph)!!

        assertSame(inheritedFont, layout.root.font)
        assertSame(inheritedFont, inheritedNode.font)
        assertEquals(0xFF112233.toInt(), inheritedNode.color)
        assertSame(inheritedShadow, inheritedNode.textShadow)
        assertSame(overriddenFont, overriddenNode.font)
        assertEquals(0xFF445566.toInt(), overriddenNode.color)
        assertSame(overriddenShadow, overriddenNode.textShadow)
        assertSame(UiTextShadow.NONE, shadowlessNode.textShadow)
    }

    @Test
    fun `vertical layout uses root origin box model and horizontal center alignment`() {
        lateinit var paragraph: Paragraph
        val root = div(
            UiStyle(
                padding = UiEdges(10f),
                width = 100f.px,
                direction = UiDirection.VERTICAL,
                horizontalAlignment = UiHorizontalAlignment.CENTER,
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
    fun `explicit size applies to the selected box`() {
        val padding = UiEdges(top = 3f, right = 10f, bottom = 7f, left = 20f)
        val contentBox = div(
            UiStyle(
                width = 100f.px,
                height = 50f,
                padding = padding,
            ),
        )
        val borderBox = div(
            UiStyle(
                width = 100f.px,
                height = 50f,
                padding = padding,
                boxSizing = UiBoxSizing.BORDER_BOX,
            ),
        )

        val contentBoxLayout = calculateLayout(contentBox, left = 5f, top = 7f, textMeasurer)
        val borderBoxLayout = calculateLayout(borderBox, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiRect(5f, 7f, 130f, 60f), contentBoxLayout.root.bounds)
        assertEquals(UiRect(25f, 10f, 100f, 50f), contentBoxLayout.root.contentBounds)
        assertEquals(UiRect(5f, 7f, 100f, 50f), borderBoxLayout.root.bounds)
        assertEquals(UiRect(25f, 10f, 70f, 40f), borderBoxLayout.root.contentBounds)
    }

    @Test
    fun `width accepts pixel and percent lengths`() {
        assertEquals(UiLengthUnit.PX, 12f.px.unit)
        assertEquals(12f, 12f.px.value)
        assertEquals(UiLengthUnit.PERCENT, 37.5f.percent.unit)
        assertEquals(37.5f, 37.5f.percent.value)
        assertFailsWith<IllegalArgumentException> { (-1f).px }
        assertFailsWith<IllegalArgumentException> { Float.NaN.percent }
        assertFailsWith<IllegalArgumentException> { Float.POSITIVE_INFINITY.percent }
    }

    @Test
    fun `percent width uses each parents content width`() {
        lateinit var child: Div
        lateinit var grandchild: Paragraph
        val root = div(
            UiStyle(
                width = 200f.px,
                padding = UiEdges(vertical = 0f, horizontal = 10f),
                boxSizing = UiBoxSizing.BORDER_BOX,
            ),
        ) {
            child = div(
                UiStyle(
                    width = 50f.percent,
                    padding = UiEdges(vertical = 0f, horizontal = 5f),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            ) {
                grandchild = p("x", UiStyle(width = 50f.percent))
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)
        val childNode = layout.nodeOf(child)!!
        val grandchildNode = layout.nodeOf(grandchild)!!

        assertEquals(180f, layout.root.contentBounds.width)
        assertEquals(90f, childNode.bounds.width)
        assertEquals(80f, childNode.contentBounds.width)
        assertEquals(40f, grandchildNode.bounds.width)
    }

    @Test
    fun `border box does not change automatic size`() {
        val paragraph = Paragraph(
            text = "abcd",
            style = UiStyle(
                padding = UiEdges(3f),
                boxSizing = UiBoxSizing.BORDER_BOX,
            ),
        )

        val layout = calculateLayout(paragraph, left = 2f, top = 4f, textMeasurer)

        assertEquals(UiRect(2f, 4f, 26f, 16f), layout.root.bounds)
        assertEquals(UiRect(5f, 7f, 20f, 10f), layout.root.contentBounds)
    }

    @Test
    fun `border box floors content size at zero when padding exceeds explicit size`() {
        val root = div(
            UiStyle(
                width = 10f.px,
                height = 5f,
                padding = UiEdges(top = 4f, right = 7f, bottom = 3f, left = 8f),
                boxSizing = UiBoxSizing.BORDER_BOX,
            ),
        )

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiRect(0f, 0f, 15f, 7f), layout.root.bounds)
        assertEquals(UiRect(8f, 4f, 0f, 0f), layout.root.contentBounds)
    }

    @Test
    fun `horizontal layout aligns the whole row to the right`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                width = 100f.px,
                direction = UiDirection.HORIZONTAL,
                horizontalAlignment = UiHorizontalAlignment.RIGHT,
            ),
        ) {
            first = p("a", UiStyle(width = 20f.px))
            second = p("b", UiStyle(width = 30f.px))
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
    fun `static offsets are ignored and relative offsets preserve normal flow space`() {
        lateinit var staticParagraph: Paragraph
        lateinit var relativeParagraph: Paragraph
        lateinit var lastParagraph: Paragraph
        val root = div(UiStyle(gap = 3f)) {
            staticParagraph = p(
                "a",
                UiStyle(left = 100f, top = 100f, right = 100f, bottom = 100f),
            )
            relativeParagraph = p(
                "b",
                UiStyle(
                    position = UiPosition.RELATIVE,
                    left = 7f,
                    top = 4f,
                    right = 100f,
                    bottom = 100f,
                ),
            )
            lastParagraph = p("c")
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)

        assertEquals(UiSize(5f, 36f), layout.size)
        assertEquals(UiRect(2f, 3f, 5f, 10f), layout.nodeOf(staticParagraph)!!.outerBounds)
        assertEquals(UiRect(9f, 20f, 5f, 10f), layout.nodeOf(relativeParagraph)!!.outerBounds)
        assertEquals(UiRect(2f, 29f, 5f, 10f), layout.nodeOf(lastParagraph)!!.outerBounds)
    }

    @Test
    fun `relative right and bottom offsets move an element in the opposite direction`() {
        lateinit var paragraph: Paragraph
        val root = div {
            paragraph = p(
                "a",
                UiStyle(
                    position = UiPosition.RELATIVE,
                    right = 4f,
                    bottom = 2f,
                ),
            )
        }

        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiRect(1f, 5f, 5f, 10f), layout.nodeOf(paragraph)!!.outerBounds)
        assertEquals(UiSize(5f, 10f), layout.size)
    }

    @Test
    fun `root position offsets do not override the layout origin`() {
        UiPosition.entries.forEach { position ->
            val root = div(
                UiStyle(
                    width = 20f.px,
                    height = 10f,
                    position = position,
                    left = 100f,
                    top = 200f,
                    right = 300f,
                    bottom = 400f,
                ),
            )

            val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

            assertEquals(UiRect(5f, 7f, 20f, 10f), layout.root.outerBounds)
        }
    }

    @Test
    fun `absolute children use insets and do not participate in flow or gap`() {
        lateinit var first: Paragraph
        lateinit var absolute: Paragraph
        lateinit var last: Paragraph
        val root = div(
            UiStyle(
                direction = UiDirection.HORIZONTAL,
                gap = 4f,
            ),
        ) {
            first = p("a")
            absolute = p(
                "absolute",
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 20f,
                    top = 15f,
                ),
            )
            last = p("bc")
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)

        assertEquals(UiSize(19f, 10f), layout.size)
        assertEquals(2f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(11f, layout.nodeOf(last)!!.outerBounds.left)
        assertEquals(UiRect(22f, 18f, 40f, 10f), layout.nodeOf(absolute)!!.outerBounds)
        assertSame(absolute, layout.elementAt(22f, 18f))
    }

    @Test
    fun `absolute right and bottom insets anchor the outer box`() {
        lateinit var paragraph: Paragraph
        val root = div(UiStyle(width = 100f.px, height = 50f)) {
            paragraph = p(
                "a",
                UiStyle(
                    width = 20f.px,
                    height = 10f,
                    position = UiPosition.ABSOLUTE,
                    right = 8f,
                    bottom = 6f,
                ),
            )
        }

        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

        assertEquals(UiRect(77f, 41f, 20f, 10f), layout.nodeOf(paragraph)!!.outerBounds)
    }

    @Test
    fun `absolute children use the nearest non-static ancestor as their containing block`() {
        lateinit var staticContainer: Div
        lateinit var absolute: Div
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 100f,
                position = UiPosition.RELATIVE,
            ),
        ) {
            div(UiStyle(width = 10f.px, height = 20f))
            staticContainer = div(UiStyle(width = 40f.px, height = 40f)) {
                absolute = div(
                    UiStyle(
                        width = 10f.px,
                        height = 10f,
                        position = UiPosition.ABSOLUTE,
                        left = 8f,
                        top = 9f,
                    ),
                )
            }
        }

        val layout = calculateLayout(root, left = 5f, top = 7f, textMeasurer)

        assertEquals(27f, layout.nodeOf(staticContainer)!!.outerBounds.top)
        assertEquals(UiRect(13f, 16f, 10f, 10f), layout.nodeOf(absolute)!!.outerBounds)
    }

    @Test
    fun `absolute percent width uses the nearest non-static containing block`() {
        lateinit var absolute: Paragraph
        val root = div(
            UiStyle(
                width = 100f.px,
                padding = UiEdges(vertical = 0f, horizontal = 10f),
                position = UiPosition.RELATIVE,
            ),
        ) {
            div {
                absolute = p(
                    "a",
                    UiStyle(
                        width = 50f.percent,
                        position = UiPosition.ABSOLUTE,
                    ),
                )
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(60f, layout.nodeOf(absolute)!!.bounds.width)
    }

    @Test
    fun `paired absolute insets stretch an automatic size inside the containing block`() {
        lateinit var absolute: Div
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 60f,
                padding = UiEdges(5f),
                position = UiPosition.RELATIVE,
            ),
        ) {
            absolute = div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 10f,
                    top = 8f,
                    right = 15f,
                    bottom = 12f,
                    margin = UiEdges(2f),
                    padding = UiEdges(3f),
                ),
            )
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)
        val node = layout.nodeOf(absolute)!!

        assertEquals(UiRect(12f, 11f, 85f, 50f), node.outerBounds)
        assertEquals(UiRect(14f, 13f, 81f, 46f), node.bounds)
        assertEquals(UiRect(17f, 16f, 75f, 40f), node.contentBounds)
    }

    @Test
    fun `position styles compose and offsets accept negative finite values`() {
        val base = UiStyle(
            position = UiPosition.ABSOLUTE,
            left = 1f,
            top = 2f,
            right = 3f,
            bottom = 4f,
        )

        val result = base.withOverrides(
            UiStyle(
                position = UiPosition.RELATIVE,
                left = -5f,
                bottom = -6f,
            ),
        )

        assertEquals(UiPosition.RELATIVE, result.position)
        assertEquals(-5f, result.left)
        assertEquals(2f, result.top)
        assertEquals(3f, result.right)
        assertEquals(-6f, result.bottom)
        assertFailsWith<IllegalArgumentException> { UiStyle(left = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiStyle(top = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { UiStyle(right = Float.NEGATIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { UiStyle(bottom = Float.NaN) }
    }

    @Test
    fun `none display removes an element and its descendants from layout`() {
        lateinit var first: Paragraph
        lateinit var hidden: Div
        lateinit var hiddenParagraph: Paragraph
        lateinit var last: Paragraph
        val root = div(
            UiStyle(
                direction = UiDirection.HORIZONTAL,
                gap = 4f,
            ),
        ) {
            first = p("a")
            hidden = div(
                UiStyle(
                    width = 100f.px,
                    height = 100f,
                    margin = UiEdges(10f),
                    padding = UiEdges(10f),
                    noneDisplay = { true },
                ),
            ) {
                hiddenParagraph = p("hidden")
            }
            last = p("bc")
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)

        assertEquals(UiSize(19f, 10f), layout.size)
        assertEquals(2f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(11f, layout.nodeOf(last)!!.outerBounds.left)
        assertNull(layout.nodeOf(hidden))
        assertNull(layout.nodeOf(hiddenParagraph))
    }

    @Test
    fun `click-driven none display dynamically recalculates an existing layout`() {
        var opened = true
        var evaluations = 0
        lateinit var conditional: Paragraph
        val root = div(
            UiStyle(
                direction = UiDirection.HORIZONTAL,
                gap = 3f,
            ),
        ) {
            p("a", onClick = { opened = !opened })
            conditional = p(
                "bc",
                UiStyle(
                    noneDisplay = {
                        evaluations++
                        !opened
                    },
                ),
            )
            p("d")
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiSize(26f, 10f), layout.size)
        assertNotNull(layout.nodeOf(conditional))

        assertTrue(layout.mouseClick(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertFalse(opened)
        assertEquals(UiSize(13f, 10f), layout.size)
        assertNull(layout.nodeOf(conditional))

        assertTrue(layout.mouseClick(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertTrue(opened)
        assertEquals(UiSize(26f, 10f), layout.size)
        assertNotNull(layout.nodeOf(conditional))
        assertTrue(evaluations >= 3)
    }

    @Test
    fun `none display root has no size and does not measure text or receive input`() {
        var textMeasured = false
        var clicked = false
        val hidden = Paragraph(
            text = "hidden",
            style = UiStyle(
                width = 100f.px,
                height = 50f,
                margin = UiEdges(10f),
                padding = UiEdges(10f),
                noneDisplay = { true },
            ),
            onClick = { clicked = true },
        )
        val failOnTextMeasurement = object : UiTextMeasurer {
            override val lineHeight = 10f

            override fun width(text: String): Float {
                textMeasured = true
                return text.length * 5f
            }
        }

        val layout = calculateLayout(hidden, left = 2f, top = 3f, failOnTextMeasurement)

        assertEquals(UiSize(0f, 0f), layout.size)
        assertEquals(UiRect(2f, 3f, 0f, 0f), layout.root.outerBounds)
        assertEquals(layout.root.outerBounds, layout.root.bounds)
        assertEquals(layout.root.outerBounds, layout.root.contentBounds)
        assertFalse(layout.root.displayed)
        assertFalse(textMeasured)
        assertNull(layout.elementAt(2f, 3f))
        assertFalse(layout.mouseClick(MouseButtonEvent(2.0, 3.0, MouseButtonInfo(0, 0))))
        assertFalse(clicked)
    }

    @Test
    fun `horizontal gap is included when aligning the whole row`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                width = 100f.px,
                direction = UiDirection.HORIZONTAL,
                horizontalAlignment = UiHorizontalAlignment.RIGHT,
                gap = 7f,
            ),
        ) {
            first = p("a", UiStyle(width = 20f.px))
            second = p("b", UiStyle(width = 30f.px))
        }

        val layout = calculateLayout(root, left = 10f, top = 20f, textMeasurer)

        assertEquals(53f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(80f, layout.nodeOf(second)!!.outerBounds.left)
    }

    @Test
    fun `vertical layout aligns the whole column to the bottom`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                height = 50f,
                verticalAlignment = UiVerticalAlignment.BOTTOM,
                gap = 4f,
            ),
        ) {
            first = p("a")
            second = p("b")
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)

        assertEquals(29f, layout.nodeOf(first)!!.outerBounds.top)
        assertEquals(43f, layout.nodeOf(second)!!.outerBounds.top)
    }

    @Test
    fun `horizontal layout aligns each child to the vertical center`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                height = 50f,
                direction = UiDirection.HORIZONTAL,
                verticalAlignment = UiVerticalAlignment.CENTER,
            ),
        ) {
            first = p("a", UiStyle(height = 10f))
            second = p("b", UiStyle(height = 20f))
        }

        val layout = calculateLayout(root, left = 2f, top = 3f, textMeasurer)

        assertEquals(23f, layout.nodeOf(first)!!.outerBounds.top)
        assertEquals(18f, layout.nodeOf(second)!!.outerBounds.top)
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
    fun `click invokes the topmost clickable div with the mouse event and reports misses`() {
        var clickCount = 0
        var receivedEvent: MouseButtonEvent? = null
        lateinit var clickableDiv: Div
        val root = div {
            clickableDiv = div(onClick = { event ->
                clickCount++
                receivedEvent = event
            }) {
                p("Run")
            }
        }
        val layout = calculateLayout(root, left = 4f, top = 6f, textMeasurer)
        val hitEvent = MouseButtonEvent(5.0, 7.0, MouseButtonInfo(1, 2))
        val missEvent = MouseButtonEvent(100.0, 100.0, MouseButtonInfo(0, 0))

        assertTrue(layout.mouseClick(hitEvent))
        assertEquals(1, clickCount)
        assertSame(hitEvent, receivedEvent)
        assertTrue(clickableDiv.dragging)
        assertFalse(layout.mouseClick(missEvent))
        assertTrue(clickableDiv.dragging)
        assertTrue(layout.mouseRelease())
        assertFalse(clickableDiv.dragging)
    }

    @Test
    fun `onClick can be used by every element type`() {
        val clicked = mutableListOf<UiElement>()
        lateinit var paragraph: Paragraph
        lateinit var innerDiv: Div
        lateinit var root: Div
        root = div(
            style = UiStyle(width = 50f.px, height = 50f),
            onClick = { clicked += root },
        ) {
            innerDiv = div(
                style = UiStyle(width = 30f.px, height = 30f),
                onClick = { clicked += innerDiv },
            ) {
                paragraph = p(
                    text = "text",
                    onClick = { clicked += paragraph },
                )
            }
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.mouseClick(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertEquals(listOf<UiElement>(paragraph), clicked)

        clicked.clear()
        paragraph.onClick = null
        assertTrue(layout.mouseClick(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertEquals(listOf<UiElement>(innerDiv), clicked)

        clicked.clear()
        innerDiv.onClick = null
        assertTrue(layout.mouseClick(MouseButtonEvent(40.0, 40.0, MouseButtonInfo(0, 0))))
        assertEquals(listOf<UiElement>(root), clicked)
    }

    @Test
    fun `div without click or drag callbacks does not consume a click`() {
        val root = div {
            div {
                p("Run")
            }
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertFalse(layout.mouseClick(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
    }

    @Test
    fun `disabled prevents onClick until the element is enabled again`() {
        var clickCount = 0
        lateinit var clickableDiv: Div
        val root = div {
            clickableDiv = div(onClick = { clickCount++ }) {
                p("Run")
            }
        }
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)
        val event = MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))

        assertFalse(clickableDiv.disabled)
        assertTrue(layout.mouseClick(event))
        assertEquals(1, clickCount)

        clickableDiv.disabled = true
        assertTrue(layout.mouseClick(event))
        assertEquals(1, clickCount)

        clickableDiv.disabled = false
        assertTrue(layout.mouseClick(event))
        assertEquals(2, clickCount)
    }

    @Test
    fun `onMouseMove can be used by every element type`() {
        val moved = mutableListOf<UiElement>()
        var receivedCoordinates: Pair<Double, Double>? = null
        lateinit var paragraph: Paragraph
        lateinit var innerDiv: Div
        lateinit var siblingDiv: Div
        lateinit var root: Div
        root = div(
            style = UiStyle(width = 50f.px, height = 50f),
            onMouseMove = { x, y ->
                moved += root
                receivedCoordinates = x to y
            },
        ) {
            innerDiv = div(
                style = UiStyle(width = 30f.px, height = 30f),
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
            siblingDiv = div(onMouseMove = { _, _ -> moved += siblingDiv }) {
                p("Div")
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
        assertEquals(listOf<UiElement>(siblingDiv), moved)

        moved.clear()
        assertTrue(layout.mouseMove(49.0, 49.0))
        assertEquals(listOf<UiElement>(root), moved)

        root.onMouseMove = null
        assertFalse(layout.mouseMove(100.0, 100.0))
    }

    @Test
    fun `drag receives current coordinates and click button info until release`() {
        val dragEvents = mutableListOf<MouseButtonEvent>()
        lateinit var paragraph: Paragraph
        val root = div(UiStyle(width = 50f.px, height = 50f)) {
            paragraph = p(
                text = "Drag",
                onDrag = { event -> dragEvents += event },
            )
        }
        val layout = calculateLayout(root, left = 4f, top = 6f, textMeasurer)
        val buttonInfo = MouseButtonInfo(1, 2)

        assertFalse(paragraph.dragging)
        assertTrue(layout.mouseClick(MouseButtonEvent(5.0, 7.0, buttonInfo)))
        assertTrue(paragraph.dragging)

        assertTrue(layout.mouseMove(100.25, 200.5))
        assertEquals(listOf(MouseButtonEvent(100.25, 200.5, buttonInfo)), dragEvents)

        assertTrue(layout.mouseRelease())
        assertFalse(paragraph.dragging)
        assertFalse(layout.mouseRelease())
        assertFalse(layout.mouseMove(100.25, 200.5))
        assertEquals(1, dragEvents.size)
    }

    @Test
    fun `mouse move invokes both hover and drag callbacks while dragging`() {
        val callbacks = mutableListOf<String>()
        val root = div(
            style = UiStyle(width = 20f.px, height = 20f),
            onMouseMove = { _, _ -> callbacks += "move" },
            onDrag = { callbacks += "drag" },
        )
        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertTrue(layout.mouseClick(MouseButtonEvent(1.0, 1.0, MouseButtonInfo(0, 0))))
        assertTrue(layout.mouseMove(2.0, 3.0))
        assertEquals(listOf("move", "drag"), callbacks)
    }

    @Test
    fun `mouse over and out update hovering state once per boundary crossing`() {
        val callbacks = mutableListOf<String>()
        lateinit var paragraph: Paragraph
        lateinit var root: Div
        root = div(
            style = UiStyle(width = 50f.px, height = 50f),
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
        val root = div(UiStyle(width = 20f.px, height = 20f)) {
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
    fun `div lays out child elements horizontally`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div {
            div(
                style = UiStyle(
                    direction = UiDirection.HORIZONTAL,
                    gap = 2f,
                ),
            ) {
                first = p("a")
                second = p("bc")
            }
        }

        val layout = calculateLayout(root, left = 0f, top = 0f, textMeasurer)

        assertEquals(UiSize(17f, 10f), layout.size)
        assertEquals(0f, layout.nodeOf(first)!!.outerBounds.left)
        assertEquals(7f, layout.nodeOf(second)!!.outerBounds.left)
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
            Mine2DFontMetrics::class.java,
            GlyphProvider::class.java,
            FontSet::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            location,
            11f,
            1f,
            Mine2DFontMetrics(ascender = 8f, descender = -2f, lineHeight = 10f),
            glyphProvider,
            fontSet,
        )
    }

    private fun backgroundDraws(
        style: UiStyle,
        rendererMaterial: Mine2DMaterial = Mine2DMaterials.COLOR,
    ): List<Pair<Int, Mine2DMaterial>> = buildList {
        style.drawBackground(rendererMaterial) { color, material ->
            add(color to material)
        }
    }
}
