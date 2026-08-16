package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DCornerRadius
import io.github.aiwao.mine2dengine.Mine2DRoundedRectRadii
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BorderRadiusTest {
    private fun UiBorderRadii.resolve(bounds: UiRect): UiRoundedBox =
        resolve(bounds, UiLengthResolver(UiSize(bounds.width, bounds.height)))

    @Test
    fun `CSS corner percentages use border box width and height independently`() {
        val roundedBox = UiBorderRadii(
            topLeft = UiCornerRadius(50f.percent, 25f.percent),
            topRight = UiCornerRadius(10f.px, 20f.px),
        ).resolve(UiRect(left = 4f, top = 6f, width = 100f, height = 40f))

        assertEquals(UiRect(4f, 6f, 100f, 40f), roundedBox.bounds)
        assertEquals(Mine2DCornerRadius(50f, 10f), roundedBox.radii.topLeft)
        assertEquals(Mine2DCornerRadius(10f, 20f), roundedBox.radii.topRight)
    }

    @Test
    fun `CSS overlapping radii use one scale factor for every corner`() {
        val roundedBox = UiBorderRadii(
            topLeft = UiCornerRadius(80f.px, 10f.px),
            topRight = UiCornerRadius(80f.px, 10f.px),
            bottomRight = UiCornerRadius(5f.px, 30f.px),
            bottomLeft = UiCornerRadius(5f.px, 30f.px),
        ).resolve(UiRect(0f, 0f, 100f, 40f))

        assertEquals(Mine2DCornerRadius(50f, 6.25f), roundedBox.radii.topLeft)
        assertEquals(Mine2DCornerRadius(3.125f, 18.75f), roundedBox.radii.bottomRight)
    }

    @Test
    fun `viewport corner units use the layout viewport dimensions`() {
        val bounds = UiRect(0f, 0f, 100f, 80f)
        val roundedBox = UiBorderRadii(
            UiCornerRadius(horizontal = 10f.vw, vertical = 10f.vh),
        ).resolve(
            bounds,
            UiLengthResolver(UiSize(width = 400f, height = 200f)),
        )

        assertEquals(Mine2DCornerRadius(40f, 20f), roundedBox.radii.topLeft)
    }

    @Test
    fun `border radius validates values and participates in defaults and cascade`() {
        assertFailsWith<IllegalArgumentException> { UiCornerRadius((-1f).px) }
        assertEquals(UiBorderRadii.ZERO, UiStyle().resolveDefaults().borderRadius)

        val first = UiBorderRadii(4f.px)
        val override = UiBorderRadii(8f.px)
        assertEquals(
            override,
            UiStyle(borderRadius = first)
                .withOverrides(UiStyle(borderRadius = override))
                .borderRadius,
        )
        assertEquals(
            first,
            UiStyle(borderRadius = first)
                .withOverrides(UiStyle(backgroundColor = -1))
                .borderRadius,
        )
    }

    @Test
    fun `box shadow follows border radius unless its legacy radius is selected`() {
        assertTrue(UiBoxShadow().followBorderRadius)
        assertFalse(UiBoxShadow(cornerRadius = 4f).followBorderRadius)
        assertFalse(UiBoxShadow(cornerRadius = 0f, followBorderRadius = false).followBorderRadius)
    }

    @Test
    fun `rounded box containment follows independently elliptical corners`() {
        val box = UiRoundedBox(
            bounds = UiRect(10f, 20f, 100f, 40f),
            radii = Mine2DRoundedRectRadii(20f, 10f),
        )

        assertFalse(box.contains(10f, 20f))
        assertFalse(box.contains(11f, 21f))
        assertTrue(box.contains(30f, 20f))
        assertTrue(box.contains(20f, 25f))
        assertTrue(box.contains(50f, 40f))
        assertFalse(box.contains(110f, 40f))
    }
}
