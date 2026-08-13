package io.github.aiwao.mine2dengine.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BorderTest {
    @Test
    fun `border values validate widths and percentages`() {
        assertFailsWith<IllegalArgumentException> { UiBorderSide((-1f).px) }
        assertFailsWith<IllegalArgumentException> { UiBorderSide(10f.percent) }
        assertFailsWith<IllegalArgumentException> { UiBorderSide(Float.NaN) }
        assertEquals(2f, UiBorderSide(2f).usedWidth)
        assertEquals(0f, UiBorderSide(2f, UiBorderStyle.NONE).usedWidth)
    }

    @Test
    fun `border has an explicit initial value and participates atomically in cascade`() {
        val first = UiBorders(1f, 0xFF112233.toInt())
        val override = UiBorders(
            top = UiBorderSide(2f, color = 0xFF445566.toInt()),
        )

        assertEquals(UiBorders.NONE, UiStyle().resolveDefaults().border)
        assertEquals(
            override,
            UiStyle(border = first)
                .withOverrides(UiStyle(border = override))
                .border,
        )
        assertEquals(
            first,
            UiStyle(border = first)
                .withOverrides(UiStyle(backgroundColor = -1))
                .border,
        )
        assertEquals(first, UiStyle(border = first).withOverrides(UiStyle()).border)
    }
}
