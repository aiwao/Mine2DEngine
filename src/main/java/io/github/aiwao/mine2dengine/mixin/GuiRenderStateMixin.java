package io.github.aiwao.mine2dengine.mixin;

import io.github.aiwao.mine2dengine.internal.render.Mine2DEffectContext;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures effect scopes when immutable deferred GUI states enter the render graph. */
@Mixin(GuiRenderState.class)
abstract class GuiRenderStateMixin {
    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void mine2dengine$captureElement(
        GuiElementRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        Mine2DEffectContext.capture(renderState);
    }

    @Inject(method = "addGlyphToCurrentLayer", at = @At("HEAD"))
    private void mine2dengine$captureGlyph(
        GuiElementRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        Mine2DEffectContext.capture(renderState);
    }

    @Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"))
    private void mine2dengine$captureBlit(
        BlitRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        Mine2DEffectContext.capture(renderState);
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void mine2dengine$captureText(
        GuiTextRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        Mine2DEffectContext.capture(renderState);
    }
}
