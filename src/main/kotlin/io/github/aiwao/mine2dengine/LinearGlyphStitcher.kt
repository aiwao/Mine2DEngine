package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.font.GlyphBitmap
import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.textures.GpuTextureView
import io.github.aiwao.mine2dengine.internal.LinearTextTextures
import net.minecraft.client.gui.font.GlyphStitcher
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import java.util.Collections
import java.util.IdentityHashMap

/** Marks atlas textures created for [Mine2DFont] so GUI rendering can filter them linearly. */
internal class LinearGlyphStitcher(
    textureManager: TextureManager,
    texturePrefix: Identifier,
) : GlyphStitcher(textureManager, texturePrefix) {
    private val registeredTextures: MutableSet<GpuTextureView> =
        Collections.newSetFromMap(IdentityHashMap())

    override fun stitch(glyphInfo: GlyphInfo, glyphBitmap: GlyphBitmap): BakedSheetGlyph? {
        val glyph = super.stitch(glyphInfo, glyphBitmap)
        val textureView = glyph?.createGlyph(
            0f,
            0f,
            0,
            0,
            Style.EMPTY,
            0f,
            0f,
        )?.textureView()

        if (textureView != null && registeredTextures.add(textureView)) {
            LinearTextTextures.register(textureView)
        }
        return glyph
    }

    override fun reset() {
        LinearTextTextures.unregisterAll(registeredTextures)
        registeredTextures.clear()
        super.reset()
    }
}
