package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.FontOption
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.GlyphStitcher
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

    /** Returns the rendered width of [text] in GUI pixels. */
    fun width(text: String): Int {
        checkOpen()
        RenderSystem.assertOnRenderThread()
        return renderer.width(text)
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
            val fontSet = FontSet(GlyphStitcher(minecraft.textureManager, texturePrefix))

            try {
                fontSet.reload(
                    listOf(GlyphProvider.Conditional(glyphProvider, FontOption.Filter.ALWAYS_PASS)),
                    emptySet(),
                )
                return Mine2DFont(location, size, oversample, glyphProvider, fontSet)
            } catch (throwable: Throwable) {
                fontSet.close()
                glyphProvider.close()
                throw throwable
            }
        }
    }
}
