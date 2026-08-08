package io.github.aiwao.mine2dengine.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import io.github.aiwao.mine2dengine.internal.LinearTextTextures;
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowContext;
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowMemberRenderState;
import java.util.List;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlyphRenderState.class)
abstract class GlyphRenderStateMixin implements Mine2DDropShadowMemberRenderState {
    @Unique
    private List<Long> mine2dengine$dropShadowGroups = List.of();

    @Shadow
    public abstract TextRenderable renderable();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mine2dengine$captureDropShadowGroups(
        Matrix3x2fc pose,
        TextRenderable renderable,
        ScreenRectangle scissor,
        CallbackInfo callbackInfo
    ) {
        mine2dengine$dropShadowGroups = Mine2DDropShadowContext.currentGroups();
    }

    @Override
    public List<Long> mine2dengineDropShadowGroups() {
        return mine2dengine$dropShadowGroups;
    }

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
