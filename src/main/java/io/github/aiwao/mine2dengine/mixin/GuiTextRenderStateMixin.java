package io.github.aiwao.mine2dengine.mixin;

import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowContext;
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowMemberRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextShadowContext;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextShadowRenderState;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains Mine2D shadow metadata on Minecraft's deferred text render state. */
@Mixin(GuiTextRenderState.class)
abstract class GuiTextRenderStateMixin implements
    Mine2DTextShadowRenderState,
    Mine2DDropShadowMemberRenderState {
    @Unique
    private float mine2dengine$blurRadius = Float.NaN;

    @Unique
    private List<Long> mine2dengine$dropShadowGroups = List.of();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mine2dengine$captureTextShadow(
        Font font,
        FormattedCharSequence text,
        Matrix3x2fc pose,
        int x,
        int y,
        int color,
        int backgroundColor,
        boolean dropShadow,
        boolean includeEmpty,
        ScreenRectangle scissor,
        CallbackInfo callbackInfo
    ) {
        mine2dengine$dropShadowGroups = Mine2DDropShadowContext.currentGroups();
        Float blurRadius = Mine2DTextShadowContext.currentBlurRadius();
        if (blurRadius != null) {
            mine2dengine$blurRadius = blurRadius;
        }
    }

    @Override
    public float mine2dengineBlurRadius() {
        return mine2dengine$blurRadius;
    }

    @Override
    public List<Long> mine2dengineDropShadowGroups() {
        return mine2dengine$dropShadowGroups;
    }
}
