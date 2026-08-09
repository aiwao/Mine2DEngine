package io.github.aiwao.mine2dengine

import io.github.aiwao.mine2dengine.layout.UiVerticalAlignment
import io.github.aiwao.mine2dengine.layout.alignedTop
import io.github.aiwao.mine2dengine.layout.textRendererY
import kotlin.test.Test
import kotlin.test.assertEquals

class Mine2DFontMetricsTest {
    @Test
    fun `normalizes FreeType metrics and centers their extents in the line box`() {
        val metrics = calculateFontMetrics(
            ascender26Dot6 = 1_280L,
            descender26Dot6 = -256L,
            lineHeight26Dot6 = 1_792L,
            oversample = 2f,
        )

        assertEquals(10f, metrics.ascender)
        assertEquals(-2f, metrics.descender)
        assertEquals(14f, metrics.lineHeight)
        assertEquals(11f, metrics.baselineFromLineTop)
        assertEquals(4f, metrics.rendererOffsetFromLineTop)

        val typographicTop = metrics.baselineFromLineTop - metrics.ascender
        val typographicBottom = metrics.baselineFromLineTop - metrics.descender
        assertEquals(typographicTop, metrics.lineHeight - typographicBottom)
    }

    @Test
    fun `centered text renderer origin preserves the font baseline`() {
        val metrics = calculateFontMetrics(
            ascender26Dot6 = 640L,
            descender26Dot6 = -128L,
            lineHeight26Dot6 = 896L,
            oversample = 1f,
        )
        val lineBoxTop = alignedTop(
            availableHeight = 40f,
            itemHeight = metrics.lineHeight,
            alignment = UiVerticalAlignment.CENTER,
        )

        assertEquals(13f, lineBoxTop)
        assertEquals(
            17f,
            textRendererY(
                lineBoxTop = lineBoxTop,
                lineIndex = 0,
                lineHeight = metrics.lineHeight,
                rendererOffsetFromLineTop = metrics.rendererOffsetFromLineTop,
            ),
        )
    }

    @Test
    fun `two-times oversampling can produce a quarter-unit renderer offset`() {
        val metrics = calculateFontMetrics(
            ascender26Dot6 = 1_216L,
            descender26Dot6 = -256L,
            lineHeight26Dot6 = 1_408L,
            oversample = 2f,
        )

        assertEquals(2.25f, metrics.rendererOffsetFromLineTop)
    }
}
