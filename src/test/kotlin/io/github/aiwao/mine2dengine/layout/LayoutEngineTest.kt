package io.github.aiwao.mine2dengine.layout

import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutEngineTest {
    private val textMeasurer = object : UiTextMeasurer {
        override val lineHeight: Float = 10f
        override val baselineFromLineTop: Float = 7f

        override fun width(text: String): Float = text.length * 5f
    }

    private fun layout(
        root: UiElement,
        width: Float = 100f,
        height: Float = 100f,
        left: Float = 0f,
        top: Float = 0f,
        styleSheets: Iterable<StyleSheet> = emptyList(),
    ): UiLayout = calculateLayout(
        root = root,
        viewport = UiRect(left, top, width, height),
        textMeasurer = textMeasurer,
        styleSheets = styleSheets,
    )

    @Test
    fun `CSS value types validate property-specific ranges`() {
        assertEquals(UiLengthUnit.PX, 12f.px.unit)
        assertEquals(UiLengthUnit.PERCENT, 25f.percent.unit)
        assertEquals(UiLengthUnit.VW, 30f.vw.unit)
        assertEquals(UiLengthUnit.VH, 40f.vh.unit)
        assertEquals(UiLengthUnit.VMIN, 50f.vmin.unit)
        assertEquals(UiLengthUnit.VMAX, 60f.vmax.unit)
        assertEquals(-4f, (-4f).px.value)
        assertFailsWith<IllegalArgumentException> { Float.NaN.px }
        assertFailsWith<IllegalArgumentException> { UiStyle(width = (-1f).px) }
        assertFailsWith<IllegalArgumentException> { UiStyle(minWidth = UiSizeValue.NONE) }
        assertFailsWith<IllegalArgumentException> { UiStyle(maxWidth = UiSizeValue.AUTO) }
        assertFailsWith<IllegalArgumentException> { UiStyle(padding = UiPaddings((-1f).px)) }
        assertFailsWith<IllegalArgumentException> { UiStyle(gap = (-1f).px) }
        assertFailsWith<IllegalArgumentException> { UiStyle(flexGrow = -1f) }
        assertFailsWith<IllegalArgumentException> { UiStyle(flexShrink = Float.NaN) }
    }

    @Test
    fun `viewport units use the viewport instead of the containing block`() {
        lateinit var percentageChild: Div
        lateinit var viewportChild: Div
        val root = div(UiStyle(width = 200f.px)) {
            percentageChild = div(UiStyle(width = 50f.percent, height = 10f.px))
            viewportChild = div(
                UiStyle(
                    width = 50f.vw,
                    height = 25f.vh,
                    padding = UiPaddings(
                        top = 10f.vw,
                        right = 10f.vh,
                    ),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            )
        }

        val result = layout(root, width = 400f, height = 240f)

        assertEquals(100f, result.nodeOf(percentageChild)!!.bounds.width)
        val viewportNode = result.nodeOf(viewportChild)!!
        assertEquals(UiRect(0f, 10f, 200f, 60f), viewportNode.bounds)
        assertEquals(UiRect(0f, 50f, 176f, 20f), viewportNode.contentBounds)
    }

    @Test
    fun `viewport min and max units follow the shorter and longer dimensions`() {
        lateinit var minimumChild: Div
        lateinit var maximumChild: Div
        val root = div {
            minimumChild = div(UiStyle(width = 10f.vmin, height = 10f.px))
            maximumChild = div(UiStyle(width = 10f.vmax, height = 10f.px))
        }
        val result = layout(root, width = 300f, height = 200f)

        assertEquals(20f, result.nodeOf(minimumChild)!!.bounds.width)
        assertEquals(30f, result.nodeOf(maximumChild)!!.bounds.width)

        result.updateViewport(UiRect(0f, 0f, 100f, 400f))

        assertEquals(10f, result.nodeOf(minimumChild)!!.bounds.width)
        assertEquals(40f, result.nodeOf(maximumChild)!!.bounds.width)

        result.updateViewport(UiRect(0f, 0f, 250f, 250f))

        assertEquals(25f, result.nodeOf(minimumChild)!!.bounds.width)
        assertEquals(25f, result.nodeOf(maximumChild)!!.bounds.width)
    }

    @Test
    fun `viewport gaps stay definite during intrinsic sizing`() {
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = UiSizeValue.MIN_CONTENT,
                columnGap = 10f.vw,
            ),
        ) {
            div(UiStyle(width = 20f.px, height = 10f.px))
            div(UiStyle(width = 30f.px, height = 10f.px))
        }

        val result = layout(root, width = 200f, height = 100f)

        assertEquals(70f, result.root.contentBounds.width)
    }

    @Test
    fun `viewport units resolve absolute insets on their own axes`() {
        lateinit var absolute: Div
        val root = div(
            UiStyle(
                position = UiPosition.RELATIVE,
                width = 200f.px,
                height = 100f.px,
            ),
        ) {
            absolute = div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 10f.vw,
                    top = 10f.vh,
                    width = 10f.px,
                    height = 10f.px,
                ),
            )
        }

        val result = layout(root, width = 400f, height = 200f)

        assertEquals(UiRect(40f, 20f, 10f, 10f), result.nodeOf(absolute)!!.bounds)
    }

    @Test
    fun `overflow shorthand longhands and cross-axis computation follow CSS cascade`() {
        val sameDeclaration = UiStyle(
            overflow = UiOverflow(UiOverflowValue.HIDDEN),
            overflowX = UiOverflowValue.CLIP,
        ).resolveDefaults()
        assertEquals(UiOverflowValue.CLIP, sameDeclaration.overflow.x)
        assertEquals(UiOverflowValue.HIDDEN, sameDeclaration.overflow.y)

        val shorthandOverride = UiStyle(overflowX = UiOverflowValue.AUTO)
            .withOverrides(UiStyle(overflow = UiOverflow(UiOverflowValue.HIDDEN)))
            .resolveDefaults()
        assertEquals(UiOverflowValue.HIDDEN, shorthandOverride.overflow.x)
        assertEquals(UiOverflowValue.HIDDEN, shorthandOverride.overflow.y)

        val crossAxis = UiStyle(
            overflowX = UiOverflowValue.VISIBLE,
            overflowY = UiOverflowValue.AUTO,
        ).resolveDefaults()
        assertEquals(UiOverflowValue.AUTO, crossAxis.overflow.x)
        assertEquals(UiOverflowValue.AUTO, crossAxis.overflow.y)

        val singleAxis = UiStyle(
            overflowX = UiOverflowValue.CLIP,
            overflowY = UiOverflowValue.AUTO,
        ).resolveDefaults()
        assertEquals(UiOverflowValue.CLIP, singleAxis.overflow.x)
        assertEquals(UiOverflowValue.AUTO, singleAxis.overflow.y)
    }

    @Test
    fun `UA style makes div and p block boxes`() {
        lateinit var paragraph: Paragraph
        val root = div {
            paragraph = p("text")
        }

        val layout = layout(root, width = 80f)

        assertEquals(UiDisplay.BLOCK, layout.root.styleProvider().display)
        assertEquals(UiDisplay.BLOCK, layout.nodeOf(paragraph)!!.styleProvider().display)
        assertEquals(80f, layout.root.bounds.width)
        assertEquals(80f, layout.nodeOf(paragraph)!!.bounds.width)
    }

    @Test
    fun `block auto width fills its containing block and auto height fits content`() {
        lateinit var child: Paragraph
        val root = div(UiStyle(padding = UiPaddings(10f))) {
            child = p("ab")
        }

        val result = layout(root, width = 100f)

        assertEquals(UiRect(0f, 0f, 100f, 30f), result.root.bounds)
        assertEquals(UiRect(10f, 10f, 80f, 10f), result.nodeOf(child)!!.bounds)
    }

    @Test
    fun `explicit block width distributes horizontal auto margins`() {
        lateinit var child: Div
        val root = div(UiStyle(padding = UiPaddings(horizontal = 10f, vertical = 0f))) {
            child = div(
                UiStyle(
                    width = 20f.px,
                    height = 5f.px,
                    margin = UiMargins(
                        right = UiMarginValue.AUTO,
                        left = UiMarginValue.AUTO,
                    ),
                ),
            )
        }

        val result = layout(root, width = 100f)

        assertEquals(40f, result.nodeOf(child)!!.bounds.left)
        assertEquals(80f, result.nodeOf(child)!!.outerBounds.width)
    }

    @Test
    fun `percent sizes and padding resolve from the containing block`() {
        val root = div(
            UiStyle(
                width = 50f.percent,
                height = 50f.percent,
                padding = UiPaddings(10f.percent),
            ),
        )

        val result = layout(root, width = 200f, height = 120f)

        assertEquals(UiRect(0f, 0f, 140f, 100f), result.root.bounds)
        assertEquals(UiRect(20f, 20f, 100f, 60f), result.root.contentBounds)
    }

    @Test
    fun `border box sizing includes padding in the declared size`() {
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 50f.px,
                padding = UiPaddings(vertical = 5f, horizontal = 10f),
                boxSizing = UiBoxSizing.BORDER_BOX,
            ),
        )

        val result = layout(root, width = 200f, height = 100f)

        assertEquals(UiRect(0f, 0f, 100f, 50f), result.root.bounds)
        assertEquals(UiRect(10f, 5f, 80f, 40f), result.root.contentBounds)
    }

    @Test
    fun `content box sizing adds asymmetric borders outside padding`() {
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 50f.px,
                padding = UiPaddings(top = 1f.px, right = 2f.px, bottom = 3f.px, left = 4f.px),
                border = UiBorders(
                    top = UiBorderSide(5f, color = -1),
                    right = UiBorderSide(6f, color = -1),
                    bottom = UiBorderSide(7f, color = -1),
                    left = UiBorderSide(8f, color = -1),
                ),
            ),
        )

        val result = layout(root, width = 200f, height = 100f)

        assertEquals(UiRect(0f, 0f, 120f, 66f), result.root.bounds)
        assertEquals(UiRect(8f, 5f, 106f, 54f), result.root.paddingBounds)
        assertEquals(UiRect(12f, 6f, 100f, 50f), result.root.contentBounds)
        assertEquals(result.root.bounds, result.rootFragment.borderBox)
        assertEquals(result.root.paddingBounds, result.rootFragment.paddingBox)
    }

    @Test
    fun `border box sizing includes border and padding in the declared size`() {
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 50f.px,
                padding = UiPaddings(top = 1f.px, right = 2f.px, bottom = 3f.px, left = 4f.px),
                border = UiBorders(
                    top = UiBorderSide(5f),
                    right = UiBorderSide(6f),
                    bottom = UiBorderSide(7f),
                    left = UiBorderSide(8f),
                ),
                boxSizing = UiBoxSizing.BORDER_BOX,
            ),
        )

        val result = layout(root, width = 200f, height = 100f)

        assertEquals(UiRect(0f, 0f, 100f, 50f), result.root.bounds)
        assertEquals(UiRect(8f, 5f, 86f, 38f), result.root.paddingBounds)
        assertEquals(UiRect(12f, 6f, 80f, 34f), result.root.contentBounds)
    }

    @Test
    fun `none border style has zero used width`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                border = UiBorders(
                    UiBorderSide(100f, style = UiBorderStyle.NONE, color = -1),
                ),
            ),
        )

        val result = layout(root)

        assertEquals(UiRect(0f, 0f, 20f, 10f), result.root.bounds)
        assertEquals(result.root.bounds, result.root.paddingBounds)
    }

    @Test
    fun `auto block width subtracts borders from its content area`() {
        val root = div(
            UiStyle(
                height = 10f.px,
                border = UiBorders(5f),
            ),
        )

        val result = layout(root, width = 100f)

        assertEquals(UiRect(0f, 0f, 100f, 20f), result.root.bounds)
        assertEquals(UiRect(5f, 5f, 90f, 10f), result.root.contentBounds)
    }

    @Test
    fun `flex wrapping counts border widths in item outer size`() {
        lateinit var second: Div
        lateinit var third: Div
        val itemStyle = UiStyle(
            width = 20f.px,
            height = 10f.px,
            border = UiBorders(5f),
        )
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                flexWrap = UiFlexWrap.WRAP,
                width = 60f.px,
            ),
        ) {
            div(itemStyle)
            second = div(itemStyle)
            third = div(itemStyle)
        }

        val result = layout(root, width = 100f)

        assertEquals(UiRect(30f, 0f, 30f, 20f), result.nodeOf(second)!!.bounds)
        assertEquals(UiRect(0f, 20f, 30f, 20f), result.nodeOf(third)!!.bounds)
    }

    @Test
    fun `absolute inset sizing uses the containing padding box and child borders`() {
        lateinit var child: Div
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 50f.px,
                boxSizing = UiBoxSizing.BORDER_BOX,
                position = UiPosition.RELATIVE,
                padding = UiPaddings(5f),
                border = UiBorders(10f),
            ),
        ) {
            child = div(
                UiStyle(
                    height = 10f.px,
                    position = UiPosition.ABSOLUTE,
                    left = 0f.px,
                    top = 0f.px,
                    right = 0f.px,
                    border = UiBorders(2f),
                ),
            )
        }

        val result = layout(root, width = 200f, height = 100f)

        assertEquals(UiRect(10f, 10f, 80f, 30f), result.root.paddingBounds)
        assertEquals(UiRect(10f, 10f, 80f, 14f), result.nodeOf(child)!!.bounds)
        assertEquals(UiRect(12f, 12f, 76f, 10f), result.nodeOf(child)!!.contentBounds)
    }

    @Test
    fun `min and max sizes clamp the preferred size`() {
        val minimum = layout(
            div(UiStyle(width = 10f.px, minWidth = 30f.px, height = 1f.px)),
            width = 100f,
        )
        val maximum = layout(
            div(UiStyle(width = 80f.px, maxWidth = 40f.px, height = 1f.px)),
            width = 100f,
        )

        assertEquals(30f, minimum.root.contentBounds.width)
        assertEquals(40f, maximum.root.contentBounds.width)
    }

    @Test
    fun `minimum size wins when it exceeds maximum size`() {
        val result = layout(
            div(
                UiStyle(
                    width = 30f.px,
                    height = 30f.px,
                    minWidth = 60f.px,
                    minHeight = 50f.px,
                    maxWidth = 40f.px,
                    maxHeight = 20f.px,
                ),
            ),
        )

        assertEquals(60f, result.root.contentBounds.width)
        assertEquals(50f, result.root.contentBounds.height)
    }

    @Test
    fun `percentage min and max sizes use the containing block axes`() {
        val result = layout(
            div(
                UiStyle(
                    width = 20f.percent,
                    height = 80f.percent,
                    minWidth = 50f.percent,
                    maxHeight = 50f.percent,
                ),
            ),
            width = 200f,
            height = 100f,
        )

        assertEquals(100f, result.root.contentBounds.width)
        assertEquals(50f, result.root.contentBounds.height)
    }

    @Test
    fun `border box min and max lengths constrain the border box`() {
        val minimum = layout(
            div(
                UiStyle(
                    width = 10f.px,
                    minWidth = 40f.px,
                    padding = UiPaddings(vertical = 0f, horizontal = 10f),
                    border = UiBorders(2f),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            ),
        )
        val maximum = layout(
            div(
                UiStyle(
                    width = 80f.px,
                    maxWidth = 40f.px,
                    padding = UiPaddings(vertical = 0f, horizontal = 10f),
                    border = UiBorders(2f),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            ),
        )

        assertEquals(40f, minimum.root.bounds.width)
        assertEquals(16f, minimum.root.contentBounds.width)
        assertEquals(40f, maximum.root.bounds.width)
        assertEquals(16f, maximum.root.contentBounds.width)
    }

    @Test
    fun `intrinsic sizing keywords are independent of box sizing`() {
        val preferred = layout(
            Paragraph(
                "abcdefgh",
                UiStyle(
                    width = UiSizeValue.MIN_CONTENT,
                    padding = UiPaddings(vertical = 0f, horizontal = 10f),
                    border = UiBorders(2f),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            ),
            width = 200f,
        )
        val maximum = layout(
            Paragraph(
                "abcdefgh",
                UiStyle(
                    width = 100f.px,
                    maxWidth = UiSizeValue.MAX_CONTENT,
                    padding = UiPaddings(vertical = 0f, horizontal = 10f),
                    border = UiBorders(2f),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            ),
            width = 200f,
        )

        assertEquals(40f, preferred.root.contentBounds.width)
        assertEquals(64f, preferred.root.bounds.width)
        assertEquals(40f, maximum.root.contentBounds.width)
        assertEquals(64f, maximum.root.bounds.width)
    }

    @Test
    fun `fit content argument is converted through the sizing box once`() {
        val contentBox = layout(
            Paragraph(
                "a a a a a",
                UiStyle(
                    width = UiSizeValue.FitContent(30f.px),
                    padding = UiPaddings(vertical = 0f, horizontal = 10f),
                ),
            ),
            width = 200f,
        )
        val borderBox = layout(
            Paragraph(
                "a a a a a",
                UiStyle(
                    width = UiSizeValue.FitContent(40f.px),
                    padding = UiPaddings(vertical = 0f, horizontal = 10f),
                    boxSizing = UiBoxSizing.BORDER_BOX,
                ),
            ),
            width = 200f,
        )

        assertEquals(30f, contentBox.root.contentBounds.width)
        assertEquals(50f, contentBox.root.bounds.width)
        assertEquals(20f, borderBox.root.contentBounds.width)
        assertEquals(40f, borderBox.root.bounds.width)
    }

    @Test
    fun `adjacent block margins collapse including negative margins`() {
        lateinit var second: Div
        val root = div {
            div(UiStyle(height = 10f.px, margin = UiMargins(bottom = 20f.px)))
            second = div(
                UiStyle(
                    height = 10f.px,
                    margin = UiMargins(top = (-5f).px),
                ),
            )
        }

        val result = layout(root)

        assertEquals(25f, result.nodeOf(second)!!.bounds.top)
        assertEquals(35f, result.root.contentBounds.height)
    }

    @Test
    fun `normal white space collapses and wraps into line fragments`() {
        val paragraph = Paragraph("aa   bb cc", UiStyle(width = 30f.px))

        val result = layout(paragraph)

        assertEquals(20f, result.root.contentBounds.height)
        assertEquals(listOf("aa", " bb", "cc"), result.root.textFragments.map { it.text })
        assertEquals(listOf(0f, 0f, 10f), result.root.textFragments.map { it.bounds.top })
    }

    @Test
    fun `pre white space preserves spaces and hard line breaks`() {
        val paragraph = Paragraph(
            "a  b\nc",
            UiStyle(width = 40f.px, whiteSpace = UiWhiteSpace.PRE),
        )

        val result = layout(paragraph)

        assertEquals(listOf("a  b", "c"), result.root.textFragments.map { it.text })
        assertEquals(20f, result.root.contentBounds.height)
    }

    @Test
    fun `text align positions each produced line`() {
        val paragraph = Paragraph(
            "aa",
            UiStyle(width = 40f.px, textAlign = UiTextAlign.CENTER),
        )

        val result = layout(paragraph)

        assertEquals(15f, result.root.textFragments.single().bounds.left)
    }

    @Test
    fun `display none omits the entire subtree from boxes and input`() {
        lateinit var hidden: Div
        lateinit var descendant: Paragraph
        val root = div {
            hidden = div(UiStyle(display = UiDisplay.NONE)) {
                descendant = p("hidden")
            }
            p("shown")
        }

        val result = layout(root)

        assertNull(result.nodeOf(hidden))
        assertNull(result.nodeOf(descendant))
        assertTrue(result.fragmentsOf(hidden).isEmpty())
        assertEquals(10f, result.root.contentBounds.height)
    }

    @Test
    fun `display contents suppresses only the principal box`() {
        lateinit var contents: Paragraph
        val root = div {
            contents = p(
                "text",
                UiStyle(display = UiDisplay.CONTENTS, color = 0x12345678),
            )
        }

        val result = layout(root)

        assertNull(result.nodeOf(contents))
        assertTrue(result.fragmentsOf(contents).isEmpty())
        assertEquals("text", result.root.textFragments.single().text)
        assertEquals(0x12345678, result.root.styledTextFragments.single().textStyle!!.color)
    }

    @Test
    fun `flex grow distributes positive free space by factor`() {
        lateinit var first: Div
        lateinit var second: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 90f.px, height = 20f.px)) {
            first = div(
                UiStyle(flexGrow = 1f, flexBasis = 0f.px, minWidth = 0f.px),
            )
            second = div(
                UiStyle(flexGrow = 2f, flexBasis = 0f.px, minWidth = 0f.px),
            )
        }

        val result = layout(root)

        assertClose(30f, result.nodeOf(first)!!.contentBounds.width)
        assertClose(60f, result.nodeOf(second)!!.contentBounds.width)
        assertClose(30f, result.nodeOf(second)!!.bounds.left)
    }

    @Test
    fun `flex factors below one leave the undistributed fraction as free space`() {
        lateinit var child: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 100f.px)) {
            child = div(
                UiStyle(
                    flexGrow = 0.25f,
                    flexBasis = 0f.px,
                    minWidth = 0f.px,
                    height = 5f.px,
                ),
            )
        }

        val result = layout(root)

        assertClose(25f, result.nodeOf(child)!!.contentBounds.width)
    }

    @Test
    fun `flex freeze loop resolves mixed min and max violations`() {
        lateinit var minimum: Div
        lateinit var maximum: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 40f.px)) {
            minimum = div(
                UiStyle(
                    flexGrow = 1f,
                    flexBasis = 0f.px,
                    minWidth = 20f.px,
                    height = 5f.px,
                ),
            )
            maximum = div(
                UiStyle(
                    flexGrow = 3f,
                    flexBasis = 10f.px,
                    minWidth = 0f.px,
                    maxWidth = 12f.px,
                    height = 5f.px,
                ),
            )
        }

        val result = layout(root)

        assertClose(28f, result.nodeOf(minimum)!!.contentBounds.width)
        assertClose(12f, result.nodeOf(maximum)!!.contentBounds.width)
    }

    @Test
    fun `explicit flex minimum wins over a smaller maximum`() {
        lateinit var child: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 100f.px)) {
            child = div(
                UiStyle(
                    flexGrow = 1f,
                    flexBasis = 0f.px,
                    minWidth = 60f.px,
                    maxWidth = 20f.px,
                    height = 5f.px,
                ),
            )
        }

        val result = layout(root)

        assertEquals(60f, result.nodeOf(child)!!.contentBounds.width)
    }

    @Test
    fun `flex shrink uses scaled shrink factors`() {
        lateinit var first: Div
        lateinit var second: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 100f.px)) {
            first = div(
                UiStyle(width = 80f.px, height = 10f.px, minWidth = 0f.px),
            )
            second = div(
                UiStyle(width = 80f.px, height = 10f.px, minWidth = 0f.px),
            )
        }

        val result = layout(root)

        assertClose(50f, result.nodeOf(first)!!.contentBounds.width)
        assertClose(50f, result.nodeOf(second)!!.contentBounds.width)
    }

    @Test
    fun `shrink factors below one retain part of negative free space`() {
        lateinit var first: Div
        lateinit var second: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 100f.px)) {
            first = div(
                UiStyle(
                    width = 80f.px,
                    height = 5f.px,
                    minWidth = 0f.px,
                    flexShrink = 0.25f,
                ),
            )
            second = div(
                UiStyle(
                    width = 80f.px,
                    height = 5f.px,
                    minWidth = 0f.px,
                    flexShrink = 0.25f,
                ),
            )
        }

        val result = layout(root)

        assertClose(65f, result.nodeOf(first)!!.contentBounds.width)
        assertClose(65f, result.nodeOf(second)!!.contentBounds.width)
    }

    @Test
    fun `automatic flex minimum preserves min content size`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 50f.px)) {
            first = p("abcdefgh")
            second = p("abcdefgh")
        }

        val result = layout(root)

        assertEquals(40f, result.nodeOf(first)!!.contentBounds.width)
        assertEquals(40f, result.nodeOf(second)!!.contentBounds.width)
        assertEquals(40f, result.nodeOf(second)!!.outerBounds.left)
    }

    @Test
    fun `automatic flex minimum is capped by a specified main size`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 40f.px)) {
            first = p("abcdefgh", UiStyle(width = 30f.px))
            second = p("abcdefgh", UiStyle(width = 30f.px))
        }

        val result = layout(root)

        assertClose(30f, result.nodeOf(first)!!.contentBounds.width)
        assertClose(30f, result.nodeOf(second)!!.contentBounds.width)
    }

    @Test
    fun `justify content distributes remaining main axis space`() {
        lateinit var first: Div
        lateinit var second: Div
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 100f.px,
                justifyContent = UiJustifyContent.SPACE_BETWEEN,
            ),
        ) {
            first = div(UiStyle(width = 10f.px, height = 10f.px, flexShrink = 0f))
            second = div(UiStyle(width = 10f.px, height = 10f.px, flexShrink = 0f))
        }

        val result = layout(root)

        assertEquals(0f, result.nodeOf(first)!!.bounds.left)
        assertEquals(90f, result.nodeOf(second)!!.bounds.left)
    }

    @Test
    fun `align items supports center and stretch`() {
        lateinit var centered: Div
        lateinit var stretched: Div
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 60f.px,
                height = 40f.px,
                alignItems = UiAlignItems.CENTER,
            ),
        ) {
            centered = div(UiStyle(width = 10f.px, height = 10f.px))
            stretched = div(
                UiStyle(
                    width = 10f.px,
                    alignSelf = UiAlignSelf.STRETCH,
                ),
            )
        }

        val result = layout(root)

        assertEquals(15f, result.nodeOf(centered)!!.bounds.top)
        assertEquals(40f, result.nodeOf(stretched)!!.bounds.height)
    }

    @Test
    fun `flex wrap creates lines and applies cross axis gap`() {
        lateinit var third: Div
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 50f.px,
                flexWrap = UiFlexWrap.WRAP,
                rowGap = 5f.px,
            ),
        ) {
            repeat(3) { index ->
                val child = div(
                    UiStyle(width = 20f.px, height = 10f.px, flexShrink = 0f),
                )
                if (index == 2) third = child
            }
        }

        val result = layout(root)

        assertEquals(15f, result.nodeOf(third)!!.bounds.top)
        assertEquals(25f, result.root.contentBounds.height)
    }

    @Test
    fun `align content distributes flex lines in a definite cross size`() {
        lateinit var third: Div
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 50f.px,
                height = 50f.px,
                flexWrap = UiFlexWrap.WRAP,
                alignContent = UiAlignContent.SPACE_BETWEEN,
            ),
        ) {
            repeat(3) { index ->
                val child = div(
                    UiStyle(width = 20f.px, height = 10f.px, flexShrink = 0f),
                )
                if (index == 2) third = child
            }
        }

        val result = layout(root)

        assertEquals(40f, result.nodeOf(third)!!.bounds.top)
    }

    @Test
    fun `order and row reverse use order modified document order`() {
        lateinit var first: Div
        lateinit var second: Div
        val orderedRoot = div(UiStyle(display = UiDisplay.FLEX, width = 40f.px)) {
            first = div(UiStyle(width = 10f.px, height = 5f.px, order = 2))
            second = div(UiStyle(width = 10f.px, height = 5f.px, order = 1))
        }
        val ordered = layout(orderedRoot)
        assertEquals(0f, ordered.nodeOf(second)!!.bounds.left)
        assertEquals(10f, ordered.nodeOf(first)!!.bounds.left)

        val reverseRoot = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 40f.px,
                flexDirection = UiFlexDirection.ROW_REVERSE,
            ),
        ) {
            first = div(UiStyle(width = 10f.px, height = 5f.px))
            second = div(UiStyle(width = 10f.px, height = 5f.px))
        }
        val reversed = layout(reverseRoot)
        assertEquals(30f, reversed.nodeOf(first)!!.bounds.left)
        assertEquals(20f, reversed.nodeOf(second)!!.bounds.left)
    }

    @Test
    fun `main axis auto margin absorbs positive flex free space`() {
        lateinit var pushed: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 100f.px)) {
            div(UiStyle(width = 10f.px, height = 5f.px))
            pushed = div(
                UiStyle(
                    width = 10f.px,
                    height = 5f.px,
                    margin = UiMargins(left = UiMarginValue.AUTO),
                ),
            )
        }

        val result = layout(root)

        assertEquals(90f, result.nodeOf(pushed)!!.bounds.left)
    }

    @Test
    fun `column flex distributes a definite vertical main size`() {
        lateinit var second: Div
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 40f.px,
                height = 100f.px,
                flexDirection = UiFlexDirection.COLUMN,
            ),
        ) {
            div(UiStyle(flexGrow = 1f, flexBasis = 0f.px, minHeight = 0f.px))
            second = div(UiStyle(flexGrow = 1f, flexBasis = 0f.px, minHeight = 0f.px))
        }

        val result = layout(root)

        assertEquals(50f, result.nodeOf(second)!!.bounds.top)
        assertEquals(50f, result.nodeOf(second)!!.bounds.height)
        assertEquals(40f, result.nodeOf(second)!!.bounds.width)
    }

    @Test
    fun `column flex does not stretch an auto width item when cross aligned center`() {
        lateinit var child: Paragraph
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 40f.px,
                height = 20f.px,
                flexDirection = UiFlexDirection.COLUMN,
                alignItems = UiAlignItems.CENTER,
            ),
        ) {
            child = p("xx")
        }

        val result = layout(root)

        assertEquals(UiRect(15f, 0f, 10f, 10f), result.nodeOf(child)!!.bounds)
    }

    @Test
    fun `row flex aligns padded items by their first baseline`() {
        lateinit var first: Paragraph
        lateinit var second: Paragraph
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 40f.px,
                alignItems = UiAlignItems.BASELINE,
            ),
        ) {
            first = p("a")
            second = p("b", UiStyle(padding = UiPaddings(top = 10f.px)))
        }

        val result = layout(root)

        assertEquals(
            result.nodeOf(first)!!.textFragments.single().bounds.top,
            result.nodeOf(second)!!.textFragments.single().bounds.top,
        )
    }

    @Test
    fun `row flex uses the text baseline rather than the line box bottom`() {
        lateinit var text: Paragraph
        lateinit var box: Div
        val root = div(
            UiStyle(
                display = UiDisplay.FLEX,
                width = 40f.px,
                alignItems = UiAlignItems.BASELINE,
            ),
        ) {
            text = p("a")
            box = div(UiStyle(width = 5f.px, height = 20f.px))
        }

        val result = layout(root)

        assertEquals(13f, result.nodeOf(text)!!.bounds.top)
        assertEquals(0f, result.nodeOf(box)!!.bounds.top)
    }

    @Test
    fun `scrollable flex items use zero automatic minimum in the main axis`() {
        lateinit var visible: Paragraph
        lateinit var scrollable: Paragraph
        val visibleRoot = div(UiStyle(display = UiDisplay.FLEX, width = 20f.px)) {
            visible = p("abcdefghij", UiStyle(flexShrink = 1f))
        }
        val scrollableRoot = div(UiStyle(display = UiDisplay.FLEX, width = 20f.px)) {
            scrollable = p(
                "abcdefghij",
                UiStyle(flexShrink = 1f, overflow = UiOverflow(UiOverflowValue.AUTO)),
            )
        }

        val visibleLayout = layout(visibleRoot)
        val scrollableLayout = layout(scrollableRoot)

        assertEquals(50f, visibleLayout.nodeOf(visible)!!.contentBounds.width)
        assertEquals(20f, scrollableLayout.nodeOf(scrollable)!!.contentBounds.width)
    }

    @Test
    fun `absolute boxes use the nearest positioned padding box`() {
        lateinit var absolute: Div
        val root = div(
            UiStyle(
                width = 100f.px,
                height = 50f.px,
                padding = UiPaddings(10f),
                position = UiPosition.RELATIVE,
            ),
        ) {
            div {
                absolute = div(
                    UiStyle(
                        position = UiPosition.ABSOLUTE,
                        left = 12f.px,
                        top = 7f.px,
                        width = 10f.px,
                        height = 5f.px,
                    ),
                )
            }
        }

        val result = layout(root, width = 200f, height = 100f, left = 3f, top = 4f)

        assertEquals(UiRect(15f, 11f, 10f, 5f), result.nodeOf(absolute)!!.bounds)
    }

    @Test
    fun `paired absolute insets stretch an automatic size`() {
        lateinit var absolute: Div
        val root = div(
            UiStyle(width = 100f.px, height = 60f.px, position = UiPosition.RELATIVE),
        ) {
            absolute = div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 10f.px,
                    right = 20f.px,
                    top = 5f.px,
                    bottom = 15f.px,
                ),
            )
        }

        val result = layout(root)

        assertEquals(UiRect(10f, 5f, 70f, 40f), result.nodeOf(absolute)!!.bounds)
    }

    @Test
    fun `min and max sizes constrain an absolute inset stretch`() {
        lateinit var absolute: Div
        val root = div(
            UiStyle(width = 100f.px, height = 60f.px, position = UiPosition.RELATIVE),
        ) {
            absolute = div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 10f.px,
                    right = 10f.px,
                    top = 5f.px,
                    bottom = 5f.px,
                    minWidth = 90f.px,
                    maxHeight = 30f.px,
                ),
            )
        }

        val result = layout(root)

        assertEquals(UiRect(10f, 5f, 90f, 30f), result.nodeOf(absolute)!!.bounds)
    }

    @Test
    fun `relative positioning preserves normal flow space`() {
        lateinit var relative: Div
        lateinit var following: Div
        val root = div {
            relative = div(
                UiStyle(
                    width = 10f.px,
                    height = 10f.px,
                    position = UiPosition.RELATIVE,
                    left = 5f.px,
                    top = 3f.px,
                ),
            )
            following = div(UiStyle(height = 10f.px))
        }

        val result = layout(root)

        assertEquals(UiRect(5f, 3f, 10f, 10f), result.nodeOf(relative)!!.bounds)
        assertEquals(10f, result.nodeOf(following)!!.bounds.top)
    }

    @Test
    fun `pseudo elements generate CSS boxes and flex items`() {
        val sheet = object : StyleSheet {
            override val styles = mutableListOf<StyleSheetObject>()
        }.apply {
            newStyle(
                TargetTag("div").before,
                UiPseudoStyle(
                    content = UiGeneratedContent.EmptyBox,
                    style = UiStyle(display = UiDisplay.BLOCK, width = 10f.px, height = 5f.px),
                ),
            )
        }
        lateinit var child: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, width = 30f.px)) {
            child = div(UiStyle(width = 10f.px, height = 5f.px))
        }

        val result = layout(root, styleSheets = listOf(sheet))

        assertNotNull(result.pseudoNodeOf(root, UiPseudoElement.BEFORE))
        assertEquals(10f, result.nodeOf(child)!!.bounds.left)
        assertTrue(result.fragmentsOf(root).any { it.pseudoElement == UiPseudoElement.BEFORE })
    }

    @Test
    fun `display contents on a pseudo element keeps text without generating a pseudo box`() {
        val sheet = object : StyleSheet {
            override val styles = mutableListOf<StyleSheetObject>()
        }.apply {
            newStyle(
                TargetTag("section").before,
                UiPseudoStyle(
                    content = UiGeneratedContent.Text("x"),
                    style = UiStyle(display = UiDisplay.CONTENTS, color = 0x12345678),
                ),
            )
        }
        lateinit var child: Div
        val root = div(
            style = UiStyle(display = UiDisplay.FLEX, width = 30f.px),
            tag = "section",
        ) {
            child = div(UiStyle(width = 10f.px, height = 10f.px))
        }

        val result = layout(root, styleSheets = listOf(sheet))

        assertNull(result.pseudoNodeOf(root, UiPseudoElement.BEFORE))
        assertTrue(result.rootFragment.children.none { it.pseudoElement != null })
        assertEquals("x", result.root.textFragments.single().text)
        assertEquals(0x12345678, result.root.styledTextFragments.single().textStyle!!.color)
        assertEquals(5f, result.nodeOf(child)!!.bounds.left)
    }

    @Test
    fun `pseudo flex order controls paint and hit order`() {
        val sheet = object : StyleSheet {
            override val styles = mutableListOf<StyleSheetObject>()
        }.apply {
            newStyle(
                TargetTag("section").before,
                UiPseudoStyle(
                    content = UiGeneratedContent.EmptyBox,
                    style = UiStyle(
                        display = UiDisplay.BLOCK,
                        width = 10f.px,
                        height = 10f.px,
                        order = 1,
                    ),
                ),
            )
        }
        lateinit var child: Div
        val root = div(
            style = UiStyle(display = UiDisplay.FLEX, width = 30f.px),
            tag = "section",
        ) {
            child = div(
                UiStyle(
                    width = 10f.px,
                    height = 10f.px,
                    position = UiPosition.RELATIVE,
                    left = 5f.px,
                ),
            )
        }

        val result = layout(root, styleSheets = listOf(sheet))

        assertSame(root, result.elementAt(11f, 1f))
        assertSame(child, result.elementAt(6f, 1f))
    }

    @Test
    fun `author cascade overrides UA display and inline style overrides author rules`() {
        val sheet = styleSheet(
            TargetTag("section") to UiStyle(display = UiDisplay.FLEX, width = 50f.px),
            TargetClass("card") to UiStyle(width = 30f.px),
        )
        val root = div(
            style = UiStyle(width = 40f.px),
            tag = "section",
            className = setOf("card"),
        )

        val result = layout(root, styleSheets = listOf(sheet))

        assertEquals(UiDisplay.FLEX, result.root.styleProvider().display)
        assertEquals(40f, result.root.contentBounds.width)
    }

    @Test
    fun `component style boundary and supplied content still participate in CSS layout`() {
        val componentSheet = styleSheet(
            (TargetScope descendant TargetClass("label")) to UiStyle(width = 20f.px),
        )
        val component = uiComponent(styleSheet = componentSheet) { content ->
            div(UiStyle(display = UiDisplay.FLEX), content = content)
        }
        lateinit var label: Paragraph
        val root = div {
            component(component) {
                label = p("x", className = "label")
            }
        }

        val result = layout(root)

        assertEquals(20f, result.nodeOf(label)!!.contentBounds.width)
    }

    @Test
    fun `dynamic display none triggers geometry refresh`() {
        var hidden = false
        lateinit var conditional: Paragraph
        val root = div {
            conditional = p("hidden", style = {
                UiStyle(display = if (hidden) UiDisplay.NONE else UiDisplay.BLOCK)
            })
            p("shown")
        }
        val result = layout(root)
        assertEquals(20f, result.root.contentBounds.height)

        hidden = true

        assertEquals(10f, result.size.height)
        assertNull(result.nodeOf(conditional))
    }

    @Test
    fun `hit testing and pointer callbacks use final CSS fragments`() {
        var clicked = false
        lateinit var child: Div
        val root = div(UiStyle(width = 50f.px, height = 30f.px)) {
            child = div(
                UiStyle(
                    width = 10f.px,
                    height = 10f.px,
                    position = UiPosition.RELATIVE,
                    left = 20f.px,
                ),
                onClick = { clicked = true },
            )
        }
        val result = layout(root)

        assertSame(child, result.elementAt(25f, 5f))
        assertTrue(
            result.mouseClick(MouseButtonEvent(25.0, 5.0, MouseButtonInfo(0, 0))),
        )
        assertTrue(clicked)
        assertTrue(child.dragging)
        assertTrue(result.mouseRelease())
    }

    @Test
    fun `overflow geometry includes positioned descendants and trailing padding`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                padding = UiPaddings(2f),
                overflow = UiOverflow(UiOverflowValue.AUTO),
            ),
        ) {
            div(
                UiStyle(
                    width = 20f.px,
                    height = 10f.px,
                    position = UiPosition.RELATIVE,
                    left = 20f.px,
                    top = 10f.px,
                ),
            )
        }

        val result = layout(root)

        assertEquals(UiRect(0f, 0f, 24f, 14f), result.root.paddingBounds)
        assertEquals(UiRect(0f, 0f, 44f, 24f), result.root.scrollableOverflowBounds)
        assertEquals(20f, result.root.maximumScrollX)
        assertEquals(10f, result.root.maximumScrollY)
        assertEquals(result.root.scrollableOverflowBounds, result.rootFragment.scrollableOverflow)
    }

    @Test
    fun `hidden clips hit testing but remains programmatically scrollable`() {
        lateinit var child: Div
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                overflow = UiOverflow(UiOverflowValue.HIDDEN),
            ),
        ) {
            child = div(
                UiStyle(
                    width = 20f.px,
                    height = 10f.px,
                    position = UiPosition.RELATIVE,
                    left = 20f.px,
                ),
            )
        }
        val result = layout(root)

        assertNull(result.elementAt(25f, 5f))
        assertTrue(result.scrollTo(root, 20f, 0f))
        assertEquals(UiScrollOffset(20f, 0f), result.scrollOffsetOf(root))
        assertSame(child, result.elementAt(5f, 5f))
        assertFalse(result.mouseScrolled(5.0, 5.0, -1.0, 0.0))
    }

    @Test
    fun `rounded overflow excludes descendants from corner hit regions`() {
        lateinit var child: Div
        val root = div(
            UiStyle(
                width = 40f.px,
                height = 40f.px,
                overflow = UiOverflow(UiOverflowValue.HIDDEN),
                borderRadius = UiBorderRadii(20f),
            ),
        ) {
            child = div(UiStyle(width = 40f.px, height = 40f.px))
        }
        val result = layout(root)

        assertSame(root, result.elementAt(1f, 1f))
        assertSame(child, result.elementAt(20f, 20f))
    }

    @Test
    fun `rounded overflow uses the padding edge radius inside a border`() {
        lateinit var child: Div
        val root = div(
            UiStyle(
                width = 40f.px,
                height = 40f.px,
                boxSizing = UiBoxSizing.BORDER_BOX,
                border = UiBorders(5f, -1),
                overflow = UiOverflow(UiOverflowValue.HIDDEN),
                borderRadius = UiBorderRadii(20f),
            ),
        ) {
            child = div(UiStyle(width = 30f.px, height = 30f.px))
        }
        val result = layout(root)

        assertSame(root, result.elementAt(6f, 6f))
        assertSame(child, result.elementAt(20f, 6f))
    }

    @Test
    fun `clip forbids programmatic scrolling`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                overflow = UiOverflow(UiOverflowValue.CLIP),
            ),
        ) {
            div(UiStyle(width = 40f.px, height = 20f.px))
        }
        val result = layout(root)

        assertFalse(result.scrollTo(root, 10f, 10f))
        assertEquals(UiScrollOffset(), result.scrollOffsetOf(root))
    }

    @Test
    fun `wheel scrolls auto overflow and clamps at its end`() {
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                overflow = UiOverflow(UiOverflowValue.AUTO),
            ),
        ) {
            div(UiStyle(width = 20f.px, height = 30f.px))
        }
        val result = layout(root)

        assertTrue(result.mouseScrolled(5.0, 5.0, 0.0, -1.0))
        assertEquals(UiScrollOffset(0f, 10f), result.scrollOffsetOf(root))
        assertTrue(result.mouseScrolled(5.0, 5.0, 0.0, -5.0))
        assertEquals(UiScrollOffset(0f, 20f), result.scrollOffsetOf(root))
        assertFalse(result.mouseScrolled(5.0, 5.0, 0.0, -1.0))
    }

    @Test
    fun `wheel chains from a saturated nested scroller to its ancestor`() {
        lateinit var inner: Div
        val outer = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                overflow = UiOverflow(UiOverflowValue.AUTO),
            ),
        ) {
            inner = div(
                UiStyle(
                    width = 20f.px,
                    height = 10f.px,
                    overflow = UiOverflow(UiOverflowValue.AUTO),
                ),
            ) {
                div(UiStyle(width = 20f.px, height = 20f.px))
            }
            div(UiStyle(width = 20f.px, height = 20f.px))
        }
        val result = layout(outer)

        assertTrue(result.mouseScrolled(5.0, 5.0, 0.0, -1.0))
        assertEquals(UiScrollOffset(0f, 10f), result.scrollOffsetOf(inner))
        assertEquals(UiScrollOffset(), result.scrollOffsetOf(outer))

        assertTrue(result.mouseScrolled(5.0, 5.0, 0.0, -1.0))
        assertEquals(UiScrollOffset(0f, 10f), result.scrollOffsetOf(inner))
        assertEquals(UiScrollOffset(0f, 10f), result.scrollOffsetOf(outer))
    }

    @Test
    fun `clip and visible can constrain only one physical axis`() {
        lateinit var below: Div
        val root = div(
            UiStyle(
                width = 10f.px,
                height = 10f.px,
                position = UiPosition.RELATIVE,
                overflowX = UiOverflowValue.CLIP,
                overflowY = UiOverflowValue.VISIBLE,
            ),
        ) {
            below = div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 0f.px,
                    top = 10f.px,
                    width = 10f.px,
                    height = 10f.px,
                ),
            )
            div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 10f.px,
                    top = 0f.px,
                    width = 10f.px,
                    height = 10f.px,
                ),
            )
        }
        val result = layout(root)

        assertSame(below, result.elementAt(5f, 15f))
        assertNull(result.elementAt(15f, 5f))
        assertEquals(UiOverflowValue.CLIP, result.root.overflow.x)
        assertEquals(UiOverflowValue.VISIBLE, result.root.overflow.y)
    }

    @Test
    fun `relayout clamps a retained scroll offset to new overflow geometry`() {
        val child = Div(UiStyle(width = 20f.px, height = 30f.px))
        val root = div(
            UiStyle(
                width = 20f.px,
                height = 10f.px,
                overflow = UiOverflow(UiOverflowValue.AUTO),
            ),
        ) {
            add(child)
        }
        val result = layout(root)
        assertTrue(result.scrollTo(root, 0f, 20f))

        child.style = UiStyle(width = 20f.px, height = 15f.px)
        result.relayout()

        assertEquals(5f, result.root.maximumScrollY)
        assertEquals(UiScrollOffset(0f, 5f), result.scrollOffsetOf(root))
    }

    @Test
    fun `moving a layout translates nodes fragments and hit regions`() {
        lateinit var child: Div
        val root = div(UiStyle(width = 20f.px, height = 10f.px)) {
            child = div(UiStyle(width = 5f.px, height = 5f.px))
        }
        val result = layout(root, left = 2f, top = 3f)

        result.left = 12f
        result.top = 13f

        assertEquals(UiRect(12f, 13f, 100f, 100f), result.viewport)
        assertEquals(UiRect(12f, 13f, 20f, 10f), result.root.bounds)
        assertEquals(12f, result.nodeOf(child)!!.bounds.left)
        assertEquals(12f, result.fragmentsOf(root).single().borderBox.left)
        assertSame(child, result.elementAt(13f, 14f))
    }

    @Test
    fun `updating viewport width recomputes flex wrapping in the same layout`() {
        lateinit var third: Div
        val root = div(UiStyle(display = UiDisplay.FLEX, flexWrap = UiFlexWrap.WRAP)) {
            repeat(3) { index ->
                val child = div(
                    UiStyle(width = 20f.px, height = 10f.px, flexShrink = 0f),
                )
                if (index == 2) third = child
            }
        }
        val result = layout(root, width = 50f, height = 30f)
        val previousRoot = result.root
        assertEquals(10f, result.nodeOf(third)!!.bounds.top)

        result.updateViewport(UiRect(0f, 0f, 60f, 30f))

        assertEquals(UiRect(0f, 0f, 60f, 30f), result.viewport)
        assertNotSame(previousRoot, result.root)
        assertEquals(60f, result.root.bounds.width)
        assertEquals(0f, result.nodeOf(third)!!.bounds.top)
    }

    @Test
    fun `updating viewport height recomputes percentages and absolute insets`() {
        lateinit var absolute: Div
        val root = div(
            UiStyle(height = 50f.percent, position = UiPosition.RELATIVE),
        ) {
            absolute = div(
                UiStyle(
                    position = UiPosition.ABSOLUTE,
                    left = 0f.px,
                    bottom = 0f.px,
                    width = 10f.px,
                    height = 10f.px,
                ),
            )
        }
        val result = layout(root, width = 100f, height = 100f)
        assertEquals(UiRect(0f, 40f, 10f, 10f), result.nodeOf(absolute)!!.bounds)

        result.updateViewport(UiRect(0f, 0f, 100f, 200f))

        assertEquals(100f, result.root.bounds.height)
        assertEquals(UiRect(0f, 90f, 10f, 10f), result.nodeOf(absolute)!!.bounds)
    }

    @Test
    fun `updating viewport recomputes viewport relative sizes`() {
        val root = div(UiStyle(width = 50f.vw, height = 25f.vh))
        val result = layout(root, width = 100f, height = 80f)
        assertEquals(UiRect(0f, 0f, 50f, 20f), result.root.bounds)

        result.updateViewport(UiRect(7f, 9f, 200f, 120f))

        assertEquals(UiRect(7f, 9f, 100f, 30f), result.root.bounds)
    }

    @Test
    fun `same viewport is a no-op and origin-only update does not resolve styles`() {
        var styleResolutions = 0
        val root = div(style = {
            styleResolutions += 1
            UiStyle(width = 20f.px, height = 10f.px)
        })
        val result = layout(root, width = 80f, height = 40f)
        val originalRoot = result.root
        val resolutionsAfterLayout = styleResolutions

        result.updateViewport(result.viewport)

        assertSame(originalRoot, result.root)
        assertEquals(resolutionsAfterLayout, styleResolutions)

        result.updateViewport(UiRect(5f, 7f, 80f, 40f))
        val translatedRoot = result.root

        assertEquals(resolutionsAfterLayout, styleResolutions)
        assertEquals(UiRect(5f, 7f, 20f, 10f), translatedRoot.bounds)
        assertEquals(5f, result.rootFragment.borderBox.left)

        result.updateViewport(result.viewport)
        assertSame(translatedRoot, result.root)
    }

    @Test
    fun `relayout reflects style text and child mutations at the current viewport`() {
        lateinit var paragraph: Paragraph
        val root = div(UiStyle(width = 20f.px)) {
            paragraph = p("a")
        }
        val result = layout(root, width = 100f, height = 50f, left = 2f, top = 3f)
        val previousRoot = result.root
        val added = Div(UiStyle(height = 5f.px))
        root.style = UiStyle(width = 40f.px)
        paragraph.text = "aaaa"
        root.add(added)

        result.relayout()

        assertEquals(UiRect(2f, 3f, 100f, 50f), result.viewport)
        assertNotSame(previousRoot, result.root)
        assertEquals(40f, result.root.bounds.width)
        assertEquals("aaaa", result.nodeOf(paragraph)!!.textFragments.single().text)
        assertNotNull(result.nodeOf(added))
        assertEquals(15f, result.root.bounds.height)
    }

    @Test
    fun `viewport relayout preserves input state for displayed elements and clears removed ones`() {
        lateinit var child: Div
        val root = div {
            child = div(
                UiStyle(width = 10f.px, height = 10f.px),
                onClick = {},
            )
        }
        val result = layout(root, width = 50f, height = 30f)
        result.mouseMove(5.0, 5.0)
        result.mouseClick(MouseButtonEvent(5.0, 5.0, MouseButtonInfo(0, 0)))
        assertTrue(child.hovering)
        assertTrue(child.dragging)

        result.updateViewport(UiRect(0f, 0f, 60f, 30f))

        assertTrue(child.hovering)
        assertTrue(child.dragging)
        root.children.remove(child)

        result.relayout()

        assertFalse(child.hovering)
        assertFalse(child.dragging)
        assertFalse(result.mouseRelease())
    }

    @Test
    fun `failed viewport update keeps the previous viewport and snapshot`() {
        var fail = false
        val root = div(style = {
            check(!fail) { "style failure" }
            UiStyle(height = 10f.px)
        })
        val result = layout(root, width = 100f, height = 50f)
        val previousViewport = result.viewport
        val previousRoot = result.root
        val previousFragment = result.rootFragment
        fail = true

        assertFailsWith<IllegalStateException> {
            result.updateViewport(UiRect(0f, 0f, 80f, 50f))
        }

        assertEquals(previousViewport, result.viewport)
        assertSame(previousRoot, result.root)
        assertSame(previousFragment, result.rootFragment)
    }

    @Test
    fun `viewport may be updated to an empty initial containing block`() {
        val result = layout(div(UiStyle(height = 5f.px)), width = 20f, height = 20f)

        result.updateViewport(UiRect(0f, 0f, 0f, 0f))

        assertEquals(UiRect(0f, 0f, 0f, 0f), result.viewport)
        assertEquals(0f, result.root.bounds.width)
        assertEquals(5f, result.root.bounds.height)
    }

    @Test
    fun `public layout API requires and honors the initial containing block`() {
        val root = div(UiStyle(height = 5f.px))

        val result = LayoutEngine.layout(root, UiRect(4f, 6f, 70f, 40f))

        assertEquals(UiRect(4f, 6f, 70f, 40f), result.viewport)
        assertEquals(UiRect(4f, 6f, 70f, 5f), result.root.bounds)
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.001f) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $expected, actual $actual")
    }

    private fun styleSheet(
        vararg rules: Pair<StyleSheetTarget, UiStyle>,
    ): StyleSheet = object : StyleSheet {
        override val styles = mutableListOf<StyleSheetObject>()
    }.apply {
        rules.forEach { (target, style) -> newStyle(target, style) }
    }
}
