package io.github.aiwao.mine2dengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowCompositeRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowContext;
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowMemberRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DMaterialRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DRenderBindings;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextureBinding;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextShadowContext;
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextShadowRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DUniformBinding;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Mine2D material bindings and alpha-mask drop shadows to the extracted GUI renderer. */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Shadow
    private StagedVertexBuffer.Draw previousDraw;

    @Shadow
    @Final
    private List<?> draws;

    @Shadow
    @Final
    private StagedVertexBuffer vertexBuffer;

    @Shadow
    private void enableScissor(ScreenRectangle scissor, RenderPass renderPass) {
        throw new AssertionError();
    }

    @Unique
    private final Map<StagedVertexBuffer.Draw, PreparedBindings> mine2dengine$bindingsByDraw =
        new IdentityHashMap<>();

    @Unique
    private final Map<StagedVertexBuffer.Draw, List<Long>> mine2dengine$dropShadowGroupsByDraw =
        new IdentityHashMap<>();

    @Unique
    private final Map<StagedVertexBuffer.Draw, Long> mine2dengine$dropShadowCompositeByDraw =
        new IdentityHashMap<>();

    @Unique
    private final Map<Long, DropShadowTarget> mine2dengine$dropShadowTargets =
        new LinkedHashMap<>();

    @Unique
    private boolean mine2dengine$dropShadowsPrepared;

    @Unique
    private final Map<Integer, DynamicUniformStorage<PackedUniform>> mine2dengine$uniformStorages =
        new LinkedHashMap<>();

    @Unique
    private GpuTexture mine2dengine$guiBackgroundTexture;

    @Unique
    private GpuTextureView mine2dengine$guiBackgroundView;

    @WrapMethod(method = "lambda$prepareText$0")
    private void mine2dengine$prepareTextShadow(
        GuiTextRenderState textState,
        Operation<Void> original
    ) {
        List<Long> dropShadowGroups =
            ((Mine2DDropShadowMemberRenderState) (Object) textState)
                .mine2dengineDropShadowGroups();
        float blurRadius = ((Mine2DTextShadowRenderState) (Object) textState)
            .mine2dengineBlurRadius();
        try (
            Mine2DDropShadowContext.Scope ignored =
                Mine2DDropShadowContext.useGroups(dropShadowGroups)
        ) {
            if (Float.isNaN(blurRadius)) {
                original.call(textState);
            } else {
                try (
                    Mine2DTextShadowContext.Scope textShadow =
                        Mine2DTextShadowContext.begin(blurRadius)
                ) {
                    original.call(textState);
                }
            }
        }
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"))
    private void mine2dengine$beginMaterialDraw(
        GuiElementRenderState renderState,
        CallbackInfo callbackInfo
    ) {
        if (!mine2dengine$requiresIsolatedDraw(renderState)) {
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
        List<Long> dropShadowGroups = mine2dengine$getDropShadowGroups(renderState);
        Long compositeGroup = renderState instanceof Mine2DDropShadowCompositeRenderState composite
            ? composite.mine2dengineDropShadowGroup()
            : null;
        if (bindings.isEmpty() && dropShadowGroups.isEmpty() && compositeGroup == null) {
            return;
        }

        StagedVertexBuffer.Draw draw = previousDraw;
        if (draw == null) {
            throw new IllegalStateException("Mine2D material element did not create a GUI draw");
        }

        if (!bindings.isEmpty()) {
            mine2dengine$bindingsByDraw.put(draw, mine2dengine$prepare(bindings));
        }
        if (!dropShadowGroups.isEmpty()) {
            mine2dengine$dropShadowGroupsByDraw.put(draw, dropShadowGroups);
        }
        if (compositeGroup != null) {
            mine2dengine$dropShadowCompositeByDraw.put(draw, compositeGroup);
        }

        // Also prevent a following vanilla/custom element from joining this material draw.
        previousDraw = null;
    }

    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void mine2dengine$prepareDropShadows(
        Supplier<String> label,
        RenderTarget target,
        GpuBufferSlice dynamicTransforms,
        int start,
        int end,
        CallbackInfo callbackInfo
    ) {
        if (mine2dengine$dropShadowsPrepared) {
            return;
        }
        mine2dengine$dropShadowsPrepared = true;

        Set<Long> preparing = new HashSet<>();
        for (Long groupId : mine2dengine$dropShadowCompositeByDraw.values()) {
            mine2dengine$prepareDropShadow(groupId, target, dynamicTransforms, preparing);
        }
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void mine2dengine$captureGuiBackground(CallbackInfo callbackInfo) {
        if (!mine2dengine$usesGuiBackground()) {
            return;
        }

        RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture source = mainTarget.getColorTexture();
        mine2dengine$ensureGuiBackgroundTexture(source);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            source,
            mine2dengine$guiBackgroundTexture,
            0,
            0,
            0,
            0,
            0,
            source.getWidth(0),
            source.getHeight(0)
        );
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
        mine2dengine$bindDrawBindings(renderPass, draw.mine2dengine$draw());

        original.call(renderPass, indexBuffer, indexType);
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void mine2dengine$endMaterialFrame(CallbackInfo callbackInfo) {
        mine2dengine$bindingsByDraw.clear();
        mine2dengine$dropShadowGroupsByDraw.clear();
        mine2dengine$dropShadowCompositeByDraw.clear();
        mine2dengine$closeDropShadowTargets();
        mine2dengine$dropShadowsPrepared = false;
        mine2dengine$uniformStorages.values().forEach(DynamicUniformStorage::endFrame);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void mine2dengine$closeMaterialBuffers(CallbackInfo callbackInfo) {
        mine2dengine$bindingsByDraw.clear();
        mine2dengine$dropShadowGroupsByDraw.clear();
        mine2dengine$dropShadowCompositeByDraw.clear();
        mine2dengine$closeDropShadowTargets();
        mine2dengine$dropShadowsPrepared = false;
        mine2dengine$uniformStorages.values().forEach(DynamicUniformStorage::close);
        mine2dengine$uniformStorages.clear();
        mine2dengine$closeGuiBackgroundTexture();
    }

    @Unique
    private Mine2DRenderBindings mine2dengine$getBindings(GuiElementRenderState renderState) {
        return renderState instanceof Mine2DMaterialRenderState materialRenderState
            ? materialRenderState.mine2dengineBindings()
            : Mine2DRenderBindings.EMPTY;
    }

    @Unique
    private List<Long> mine2dengine$getDropShadowGroups(GuiElementRenderState renderState) {
        return renderState instanceof Mine2DDropShadowMemberRenderState member
            ? member.mine2dengineDropShadowGroups()
            : List.of();
    }

    @Unique
    private boolean mine2dengine$requiresIsolatedDraw(GuiElementRenderState renderState) {
        return !mine2dengine$getBindings(renderState).isEmpty()
            || !mine2dengine$getDropShadowGroups(renderState).isEmpty()
            || renderState instanceof Mine2DDropShadowCompositeRenderState;
    }

    @Unique
    private boolean mine2dengine$usesGuiBackground() {
        for (PreparedBindings bindings : mine2dengine$bindingsByDraw.values()) {
            for (Mine2DTextureBinding texture : bindings.textures()) {
                if (texture.kind() == Mine2DTextureBinding.Kind.GUI_BACKGROUND) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private void mine2dengine$ensureGuiBackgroundTexture(GpuTexture source) {
        if (
            mine2dengine$guiBackgroundTexture != null
                && !mine2dengine$guiBackgroundTexture.isClosed()
                && mine2dengine$guiBackgroundTexture.getFormat() == source.getFormat()
                && mine2dengine$guiBackgroundTexture.getWidth(0) == source.getWidth(0)
                && mine2dengine$guiBackgroundTexture.getHeight(0) == source.getHeight(0)
        ) {
            return;
        }

        mine2dengine$closeGuiBackgroundTexture();
        mine2dengine$guiBackgroundTexture = RenderSystem.getDevice().createTexture(
            "Mine2D GUI background snapshot",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
            source.getFormat(),
            source.getWidth(0),
            source.getHeight(0),
            1,
            1
        );
        mine2dengine$guiBackgroundView = RenderSystem.getDevice().createTextureView(
            mine2dengine$guiBackgroundTexture
        );
    }

    @Unique
    private void mine2dengine$bindTexture(
        RenderPass renderPass,
        Mine2DTextureBinding texture
    ) {
        switch (texture.kind()) {
            case FIXED -> renderPass.bindTexture(
                texture.name(),
                texture.texture(),
                texture.sampler()
            );
            case GUI_BACKGROUND -> {
                if (mine2dengine$guiBackgroundView == null) {
                    throw new IllegalStateException("Mine2D GUI background was not captured");
                }
                renderPass.bindTexture(
                    texture.name(),
                    mine2dengine$guiBackgroundView,
                    RenderSystem.getSamplerCache().getClampToEdge(texture.filterMode())
                );
            }
        }
    }

    @Unique
    private void mine2dengine$prepareDropShadow(
        long groupId,
        RenderTarget target,
        GpuBufferSlice dynamicTransforms,
        Set<Long> preparing
    ) {
        if (mine2dengine$dropShadowTargets.containsKey(groupId)) {
            return;
        }
        if (!preparing.add(groupId)) {
            throw new IllegalStateException("Cyclic Mine2D drop-shadow group: " + groupId);
        }

        try {
            for (Object drawObject : draws) {
                GuiRendererDrawAccessor draw = (GuiRendererDrawAccessor) drawObject;
                StagedVertexBuffer.Draw stagedDraw = draw.mine2dengine$draw();
                List<Long> groups = mine2dengine$dropShadowGroupsByDraw.get(stagedDraw);
                if (groups == null || !groups.contains(groupId)) {
                    continue;
                }
                Long nestedGroup = mine2dengine$dropShadowCompositeByDraw.get(stagedDraw);
                if (nestedGroup != null) {
                    mine2dengine$prepareDropShadow(
                        nestedGroup,
                        target,
                        dynamicTransforms,
                        preparing
                    );
                }
            }

            GpuTexture source = target.getColorTexture();
            GpuTexture texture = RenderSystem.getDevice().createTexture(
                "Mine2D drop-shadow mask " + groupId,
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                source.getFormat(),
                source.getWidth(0),
                source.getHeight(0),
                1,
                1
            );
            GpuTextureView view = RenderSystem.getDevice().createTextureView(texture);
            DropShadowTarget dropShadowTarget = new DropShadowTarget(texture, view);
            mine2dengine$dropShadowTargets.put(groupId, dropShadowTarget);

            try (
                RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                        () -> "Mine2D drop-shadow mask " + groupId,
                        view,
                        Optional.of(new Vector4f(0.0F))
                    )
            ) {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                for (Object drawObject : draws) {
                    GuiRendererDrawAccessor draw = (GuiRendererDrawAccessor) drawObject;
                    StagedVertexBuffer.Draw stagedDraw = draw.mine2dengine$draw();
                    List<Long> groups = mine2dengine$dropShadowGroupsByDraw.get(stagedDraw);
                    if (groups != null && groups.contains(groupId)) {
                        mine2dengine$executeDropShadowMember(draw, renderPass);
                    }
                }
            } catch (RuntimeException | Error exception) {
                mine2dengine$dropShadowTargets.remove(groupId);
                dropShadowTarget.close();
                throw exception;
            }
        } finally {
            preparing.remove(groupId);
        }
    }

    @Unique
    private void mine2dengine$executeDropShadowMember(
        GuiRendererDrawAccessor draw,
        RenderPass renderPass
    ) {
        StagedVertexBuffer.Draw stagedDraw = draw.mine2dengine$draw();
        StagedVertexBuffer.ExecuteInfo executeInfo = vertexBuffer.getExecuteInfo(stagedDraw);
        if (executeInfo == null) {
            return;
        }

        renderPass.setPipeline(draw.mine2dengine$pipeline());
        renderPass.setVertexBuffer(0, executeInfo.vertexBuffer().slice());
        ScreenRectangle scissor = draw.mine2dengine$scissorArea();
        if (scissor == null) {
            renderPass.disableScissor();
        } else {
            enableScissor(scissor, renderPass);
        }

        TextureSetup textureSetup = draw.mine2dengine$textureSetup();
        if (textureSetup.texure0() != null) {
            renderPass.bindTexture("Sampler0", textureSetup.texure0(), textureSetup.sampler0());
        }
        if (textureSetup.texure1() != null) {
            renderPass.bindTexture("Sampler1", textureSetup.texure1(), textureSetup.sampler1());
        }
        if (textureSetup.texure2() != null) {
            renderPass.bindTexture("Sampler2", textureSetup.texure2(), textureSetup.sampler2());
        }
        mine2dengine$bindDrawBindings(renderPass, stagedDraw);
        renderPass.setIndexBuffer(executeInfo.indexBuffer(), executeInfo.indexType());
        renderPass.drawIndexed(
            executeInfo.indexCount(),
            1,
            executeInfo.firstIndex(),
            executeInfo.baseVertex(),
            0
        );
    }

    @Unique
    private void mine2dengine$bindDrawBindings(
        RenderPass renderPass,
        StagedVertexBuffer.Draw stagedDraw
    ) {
        PreparedBindings bindings = mine2dengine$bindingsByDraw.get(stagedDraw);
        if (bindings != null) {
            bindings.uniforms().forEach(renderPass::setUniform);
            for (Mine2DTextureBinding texture : bindings.textures()) {
                mine2dengine$bindTexture(renderPass, texture);
            }
        }

        Long groupId = mine2dengine$dropShadowCompositeByDraw.get(stagedDraw);
        if (groupId != null) {
            DropShadowTarget target = mine2dengine$dropShadowTargets.get(groupId);
            if (target == null) {
                throw new IllegalStateException(
                    "Mine2D drop-shadow mask was not prepared: " + groupId
                );
            }
            renderPass.bindTexture(
                "DropShadowSampler",
                target.view(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
        }
    }

    @Unique
    private void mine2dengine$closeDropShadowTargets() {
        mine2dengine$dropShadowTargets.values().forEach(DropShadowTarget::close);
        mine2dengine$dropShadowTargets.clear();
    }

    @Unique
    private void mine2dengine$closeGuiBackgroundTexture() {
        if (mine2dengine$guiBackgroundTexture != null) {
            mine2dengine$guiBackgroundTexture.close();
            mine2dengine$guiBackgroundTexture = null;
        }
        if (mine2dengine$guiBackgroundView != null) {
            mine2dengine$guiBackgroundView.close();
            mine2dengine$guiBackgroundView = null;
        }
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
    private record DropShadowTarget(GpuTexture texture, GpuTextureView view) {
        private void close() {
            view.close();
            texture.close();
        }
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
