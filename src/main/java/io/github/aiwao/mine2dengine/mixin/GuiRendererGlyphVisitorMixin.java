package io.github.aiwao.mine2dengine.mixin;

import io.github.aiwao.mine2dengine.TextShadowGlyphRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextShadowContext;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Replaces glyph states belonging to a Mine2D text shadow with shader-blurred glyph quads. */
@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer$1")
abstract class GuiRendererGlyphVisitorMixin {
    @ModifyArg(
        method = "acceptRenderable",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addGlyphToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;)V"
        )
    )
    private GuiElementRenderState mine2dengine$createTextShadowGlyph(
        GuiElementRenderState original
    ) {
        Float blurRadius = Mine2DTextShadowContext.currentBlurRadius();
        if (blurRadius == null || !(original instanceof GlyphRenderState glyph)) {
            return original;
        }
        return TextShadowGlyphRenderState.create(glyph, blurRadius);
    }
}
