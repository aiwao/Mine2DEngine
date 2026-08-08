package io.github.aiwao.mine2dengine.mixin;

import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/client/gui/render/GuiRenderer$Draw")
interface GuiRendererDrawAccessor {
    @Accessor("draw")
    StagedVertexBuffer.Draw mine2dengine$draw();
}
