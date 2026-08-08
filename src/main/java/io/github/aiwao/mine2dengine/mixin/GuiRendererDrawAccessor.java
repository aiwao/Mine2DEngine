package io.github.aiwao.mine2dengine.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/client/gui/render/GuiRenderer$Draw")
interface GuiRendererDrawAccessor {
    @Accessor("draw")
    StagedVertexBuffer.Draw mine2dengine$draw();

    @Accessor("pipeline")
    RenderPipeline mine2dengine$pipeline();

    @Accessor("textureSetup")
    TextureSetup mine2dengine$textureSetup();

    @Accessor("scissorArea")
    ScreenRectangle mine2dengine$scissorArea();
}
