package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.systems.RenderSystem
import io.github.aiwao.mine2dengine.mixin.TrueTypeGlyphProviderAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.FontOption
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import java.util.concurrent.atomic.AtomicInteger

/**
 * A TrueType font loaded from a client resource.
 *
 * Keep this object alive while queued text may still be rendered and call [close]
 * on the render thread when the font is no longer needed.
 */
class Mine2DFont private constructor(
    val location: Identifier,
    val size: Float,
    val oversample: Float,
    private val metrics: Mine2DFontMetrics,
    private val glyphProvider: GlyphProvider,
    private val fontSet: FontSet,
) : AutoCloseable {
    private var closed = false

    internal val renderer = Font(
        object : Font.Provider {
            override fun glyphs(font: FontDescription): GlyphSource = fontSet.source(false)

            override fun effect(): EffectGlyph = fontSet.whiteGlyph()
        },
    )

    /** The font-defined vertical distance between consecutive baselines in GUI units. */
    val lineHeight: Float
        get() {
            checkOpen()
            return metrics.lineHeight
        }

    /** Converts the top of this font's line box to Minecraft's fixed text origin. */
    internal val rendererOffsetFromLineTop: Float
        get() = metrics.rendererOffsetFromLineTop

    /** Returns the exact horizontal advance of [text] in GUI units. */
    fun width(text: String): Float {
        checkOpen()
        RenderSystem.assertOnRenderThread()
        return renderer.splitter.stringWidth(text)
    }

    internal fun checkOpen() {
        check(!closed) { "The font has already been closed" }
    }

    /** Releases the glyph atlas and native font data. Must run on the render thread. */
    override fun close() {
        if (closed) return
        RenderSystem.assertOnRenderThread()
        closed = true
        try {
            fontSet.close()
        } finally {
            glyphProvider.close()
        }
    }

    companion object {
        private val nextTextureId = AtomicInteger()

        /**
         * Loads a `.ttf` resource from `assets/<namespace>/font/<path>`.
         *
         * Load fonts once after client resources are available rather than once per frame.
         * This method creates GPU resources and must run on the render thread.
         */
        @JvmStatic
        @JvmOverloads
        fun load(
            location: Identifier,
            size: Float = 11f,
            oversample: Float = 1f,
        ): Mine2DFont {
            require(location.path.endsWith(".ttf", ignoreCase = true)) {
                "A TrueType font resource must use the .ttf extension"
            }
            require(size.isFinite() && size > 0f) { "Font size must be finite and positive" }
            require(oversample.isFinite() && oversample > 0f) {
                "Font oversample must be finite and positive"
            }
            RenderSystem.assertOnRenderThread()

            val minecraft = Minecraft.getInstance()
            val definition = TrueTypeGlyphProviderDefinition(
                location,
                size,
                oversample,
                TrueTypeGlyphProviderDefinition.Shift.NONE,
                "",
            )
            val loader = definition.unpack().left().orElseThrow {
                IllegalStateException("The TrueType font definition did not provide a loader")
            }
            val glyphProvider = loader.load(minecraft.resourceManager)
            val textureId = nextTextureId.incrementAndGet()
            val texturePrefix = Identifier.fromNamespaceAndPath(
                "mine2dengine",
                "dynamic_font/$textureId",
            )
            val fontSet = FontSet(LinearGlyphStitcher(minecraft.textureManager, texturePrefix))

            try {
                val metrics = readMetrics(glyphProvider, oversample)
                fontSet.reload(
                    listOf(GlyphProvider.Conditional(glyphProvider, FontOption.Filter.ALWAYS_PASS)),
                    emptySet(),
                )
                return Mine2DFont(
                    location,
                    size,
                    oversample,
                    metrics,
                    glyphProvider,
                    fontSet,
                )
            } catch (throwable: Throwable) {
                fontSet.close()
                glyphProvider.close()
                throw throwable
            }
        }
    }
}

internal data class Mine2DFontMetrics(
    val ascender: Float,
    val descender: Float,
    val lineHeight: Float,
) {
    init {
        require(ascender.isFinite()) { "Font ascender must be finite: $ascender" }
        require(descender.isFinite()) { "Font descender must be finite: $descender" }
        require(lineHeight.isFinite() && lineHeight > 0f) {
            "Font line height must be finite and positive: $lineHeight"
        }
    }

    private val glyphHeight: Float
        get() = ascender - descender

    val baselineFromLineTop: Float
        get() = (lineHeight - glyphHeight) / 2f + ascender

    val rendererOffsetFromLineTop: Float
        get() = baselineFromLineTop - MINECRAFT_TEXT_BASELINE
}

internal fun calculateFontMetrics(
    ascender26Dot6: Long,
    descender26Dot6: Long,
    lineHeight26Dot6: Long,
    oversample: Float,
): Mine2DFontMetrics {
    require(oversample.isFinite() && oversample > 0f) {
        "Font oversample must be finite and positive"
    }
    val scale = FREE_TYPE_SUBPIXELS_PER_PIXEL * oversample
    return Mine2DFontMetrics(
        ascender = ascender26Dot6.toFloat() / scale,
        descender = descender26Dot6.toFloat() / scale,
        lineHeight = lineHeight26Dot6.toFloat() / scale,
    )
}

private fun readMetrics(
    glyphProvider: GlyphProvider,
    oversample: Float,
): Mine2DFontMetrics {
    val face = (glyphProvider as TrueTypeGlyphProviderAccessor)
        .`mine2dengine$getFace`()
    val size = checkNotNull(face.size()) { "The TrueType font does not have an active size" }
    val metrics = size.metrics()
    return calculateFontMetrics(
        ascender26Dot6 = metrics.ascender(),
        descender26Dot6 = metrics.descender(),
        lineHeight26Dot6 = metrics.height(),
        oversample = oversample,
    )
}

private const val FREE_TYPE_SUBPIXELS_PER_PIXEL = 64f
private const val MINECRAFT_TEXT_BASELINE = 7f
