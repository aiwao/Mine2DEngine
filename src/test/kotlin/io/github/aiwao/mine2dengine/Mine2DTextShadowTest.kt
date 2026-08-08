package io.github.aiwao.mine2dengine

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Mine2DTextShadowTest {
    @Test
    fun `zero blur uses one glyph sample with the requested alpha`() {
        assertEquals(
            listOf(Mine2DTextShadowSample(0f, 0f, 128)),
            calculateTextShadowSamples(blurRadius = 0f, colorAlpha = 128),
        )
    }

    @Test
    fun `blur samples stay bounded and preserve combined opacity`() {
        val samples = calculateTextShadowSamples(blurRadius = 4f, colorAlpha = 128)
        val combinedOpacity = 1.0 - samples.fold(1.0) { transparency, sample ->
            transparency * (1.0 - sample.alpha / 255.0)
        }

        assertEquals(13, samples.size)
        assertTrue(samples.all { sample -> hypot(sample.offsetX, sample.offsetY) <= 4.0001f })
        assertEquals(128.0 / 255.0, combinedOpacity, absoluteTolerance = 0.03)
    }

    @Test
    fun `large blur has a fixed sample limit and does not make every sample opaque`() {
        val samples = calculateTextShadowSamples(blurRadius = 100f, colorAlpha = 255)

        assertEquals(25, samples.size)
        assertTrue(samples.none { sample -> sample.alpha == 255 })
    }

    @Test
    fun `very low alpha blur keeps at least one visible sample`() {
        val samples = calculateTextShadowSamples(blurRadius = 4f, colorAlpha = 1)

        assertEquals(listOf(Mine2DTextShadowSample(0f, 0f, 1)), samples)
    }

    @Test
    fun `text shadow samples reject invalid inputs`() {
        assertFailsWith<IllegalArgumentException> {
            calculateTextShadowSamples(blurRadius = -1f, colorAlpha = 128)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateTextShadowSamples(blurRadius = Float.NaN, colorAlpha = 128)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateTextShadowSamples(blurRadius = 1f, colorAlpha = 256)
        }
    }
}
