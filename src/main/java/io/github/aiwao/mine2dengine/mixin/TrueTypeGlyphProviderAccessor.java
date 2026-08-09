package io.github.aiwao.mine2dengine.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the active FreeType face long enough to snapshot its scaled font metrics. */
@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeGlyphProviderAccessor {
    @Accessor("face")
    FT_Face mine2dengine$getFace();
}
