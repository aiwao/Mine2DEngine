package io.github.aiwao.mine2dengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import io.github.aiwao.mine2dengine.internal.render.Mine2DMaterialRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DRenderBindings;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextureBinding;
import io.github.aiwao.mine2dengine.internal.render.Mine2DUniformBinding;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds per-draw material UBO and sampler bindings to Minecraft's extracted GUI renderer. */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Shadow
    private StagedVertexBuffer.Draw previousDraw;

    @Unique
    private final Map<StagedVertexBuffer.Draw, PreparedBindings> mine2dengine$bindingsByDraw =
        new IdentityHashMap<>();

    @Unique
    private final Map<Integer, DynamicUniformStorage<PackedUniform>> mine2dengine$uniformStorages =
        new LinkedHashMap<>();

    @Inject(method = "addElementToMesh", at = @At("HEAD"))
    private void mine2dengine$beginMaterialDraw(
        GuiElementRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        if (mine2dengine$getBindings(renderState).isEmpty()) {
            return;
        }

        // A uniform is constant for a draw. Never append material vertices to a preceding batch.
        previousDraw = null;
    }

    @Inject(method = "addElementToMesh", at = @At("TAIL"))
    private void mine2dengine$finishMaterialDraw(
        GuiElementRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        Mine2DRenderBindings bindings = mine2dengine$getBindings(renderState);
        if (bindings.isEmpty()) {
            return;
        }

        StagedVertexBuffer.Draw draw = previousDraw;
        if (draw == null) {
            throw new IllegalStateException("Mine2D material element did not create a GUI draw");
        }

        mine2dengine$bindingsByDraw.put(draw, mine2dengine$prepare(bindings));

        // Also prevent a following vanilla/custom element from joining this material draw.
        previousDraw = null;
    }

    @WrapOperation(
        method = "executeDraw",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;setIndexBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;)V"
        )
    )
    private void mine2dengine$bindMaterial(
        RenderPass renderPass,
        GpuBuffer indexBuffer,
        IndexType indexType,
        Operation<Void> original,
        @Coerce GuiRendererDrawAccessor draw
    ) {
        PreparedBindings bindings = mine2dengine$bindingsByDraw.get(draw.mine2dengine$draw());
        if (bindings != null) {
            bindings.uniforms().forEach(renderPass::setUniform);
            for (Mine2DTextureBinding texture : bindings.textures()) {
                renderPass.bindTexture(texture.name(), texture.texture(), texture.sampler());
            }
        }

        original.call(renderPass, indexBuffer, indexType);
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void mine2dengine$endMaterialFrame(CallbackInfo callbackInfo) {
        mine2dengine$bindingsByDraw.clear();
        mine2dengine$uniformStorages.values().forEach(DynamicUniformStorage::endFrame);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void mine2dengine$closeMaterialBuffers(CallbackInfo callbackInfo) {
        mine2dengine$bindingsByDraw.clear();
        mine2dengine$uniformStorages.values().forEach(DynamicUniformStorage::close);
        mine2dengine$uniformStorages.clear();
    }

    @Unique
    private Mine2DRenderBindings mine2dengine$getBindings(GuiElementRenderState renderState) {
        return renderState instanceof Mine2DMaterialRenderState materialRenderState
            ? materialRenderState.mine2dengineBindings()
            : Mine2DRenderBindings.EMPTY;
    }

    @Unique
    private PreparedBindings mine2dengine$prepare(Mine2DRenderBindings bindings) {
        Map<String, GpuBufferSlice> uniforms = new LinkedHashMap<>();
        for (Mine2DUniformBinding uniform : bindings.uniforms()) {
            byte[] data = uniform.dataUnsafe();
            DynamicUniformStorage<PackedUniform> storage = mine2dengine$uniformStorages.computeIfAbsent(
                data.length,
                size -> new DynamicUniformStorage<>("Mine2D material UBO (" + size + " bytes)", size, 64)
            );
            uniforms.put(uniform.name(), storage.writeUniform(new PackedUniform(data)));
        }
        return new PreparedBindings(Map.copyOf(uniforms), bindings.textures());
    }

    @Unique
    private record PreparedBindings(
        Map<String, GpuBufferSlice> uniforms,
        java.util.List<Mine2DTextureBinding> textures
    ) {
    }

    @Unique
    private static final class PackedUniform implements DynamicUniformStorage.DynamicUniform {
        private final byte[] data;

        private PackedUniform(byte[] data) {
            this.data = data.clone();
        }

        @Override
        public void write(ByteBuffer buffer) {
            buffer.put(data);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                || other instanceof PackedUniform packed && Arrays.equals(data, packed.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
