package io.github.aiwao.mine2dengine.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import io.github.aiwao.mine2dengine.internal.LinearTextTextures;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GlyphRenderState.class)
abstract class GlyphRenderStateMixin {
    @Shadow
    public abstract TextRenderable renderable();

    @ModifyArg(
        method = "textureSetup",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"
        ),
        index = 0
    )
    private FilterMode mine2dengine$useLinearFiltering(FilterMode original) {
        return LinearTextTextures.contains(renderable().textureView())
            ? FilterMode.LINEAR
            : original;
    }
}
