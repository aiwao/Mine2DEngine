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
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffect;
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffectCompositeRenderState;
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffectContext;
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

/** Adds Mine2D material bindings and ordered offscreen effects to the extracted GUI renderer. */
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
    private final Map<StagedVertexBuffer.Draw, List<Mine2DEffect>> mine2dengine$effectsByDraw =
        new IdentityHashMap<>();

    @Unique
    private final Map<StagedVertexBuffer.Draw, Mine2DEffect> mine2dengine$compositeByDraw =
        new IdentityHashMap<>();

    @Unique
    private final Map<Mine2DEffect, EffectTarget> mine2dengine$effectTargets =
        new LinkedHashMap<>();

    @Unique
    private boolean mine2dengine$effectsPrepared;

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
        List<Mine2DEffect> effects = Mine2DEffectContext.capturedEffects(textState);
        float blurRadius = ((Mine2DTextShadowRenderState) (Object) textState)
            .mine2dengineBlurRadius();
        try (
            Mine2DEffectContext.Scope ignored = Mine2DEffectContext.useEffects(effects)
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
        } finally {
            Mine2DEffectContext.release(textState);
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
        List<Mine2DEffect> effects = Mine2DEffectContext.capturedEffects(renderState);
        Mine2DEffect compositeEffect =
            renderState instanceof Mine2DEffectCompositeRenderState composite
            ? composite.mine2dengineEffect()
            : null;
        Mine2DEffectContext.release(renderState);
        if (bindings.isEmpty() && effects.isEmpty() && compositeEffect == null) {
            return;
        }

        StagedVertexBuffer.Draw draw = previousDraw;
        if (draw == null) {
            throw new IllegalStateException("Mine2D material element did not create a GUI draw");
        }

        if (!bindings.isEmpty()) {
            mine2dengine$bindingsByDraw.put(draw, mine2dengine$prepare(bindings));
        }
        if (!effects.isEmpty()) {
            mine2dengine$effectsByDraw.put(draw, effects);
        }
        if (compositeEffect != null) {
            mine2dengine$compositeByDraw.put(draw, compositeEffect);
        }

        // Also prevent a following vanilla/custom element from joining this material draw.
        previousDraw = null;
    }

    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void mine2dengine$prepareEffects(
        Supplier<String> label,
        RenderTarget target,
        GpuBufferSlice dynamicTransforms,
        int start,
        int end,
        CallbackInfo callbackInfo
    ) {
        if (mine2dengine$effectsPrepared) {
            return;
        }
        mine2dengine$effectsPrepared = true;

        Set<Mine2DEffect> preparing = new HashSet<>();
        for (Mine2DEffect effect : mine2dengine$compositeByDraw.values()) {
            mine2dengine$prepareEffect(effect, target, dynamicTransforms, preparing);
        }
    }

    @WrapMethod(method = "executeDraw")
    private void mine2dengine$skipRoundedClipMembers(
        @Coerce GuiRendererDrawAccessor draw,
        RenderPass renderPass,
        Operation<Void> original
    ) {
        List<Mine2DEffect> effects = mine2dengine$effectsByDraw.get(
            draw.mine2dengine$draw()
        );
        if (effects != null && Mine2DEffectContext.containsRoundedClip(effects)) {
            return;
        }
        original.call(draw, renderPass);
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
        mine2dengine$effectsByDraw.clear();
        mine2dengine$compositeByDraw.clear();
        mine2dengine$closeEffectTargets();
        mine2dengine$effectsPrepared = false;
        Mine2DEffectContext.clearCapturedEffects();
        mine2dengine$uniformStorages.values().forEach(DynamicUniformStorage::endFrame);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void mine2dengine$closeMaterialBuffers(CallbackInfo callbackInfo) {
        mine2dengine$bindingsByDraw.clear();
        mine2dengine$effectsByDraw.clear();
        mine2dengine$compositeByDraw.clear();
        mine2dengine$closeEffectTargets();
        mine2dengine$effectsPrepared = false;
        Mine2DEffectContext.clearCapturedEffects();
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
    private boolean mine2dengine$requiresIsolatedDraw(GuiElementRenderState renderState) {
        return !mine2dengine$getBindings(renderState).isEmpty()
            || !Mine2DEffectContext.capturedEffects(renderState).isEmpty()
            || renderState instanceof Mine2DEffectCompositeRenderState;
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
    private void mine2dengine$prepareEffect(
        Mine2DEffect effect,
        RenderTarget target,
        GpuBufferSlice dynamicTransforms,
        Set<Mine2DEffect> preparing
    ) {
        if (mine2dengine$effectTargets.containsKey(effect)) {
            return;
        }
        if (!preparing.add(effect)) {
            throw new IllegalStateException("Cyclic Mine2D effect: " + effect);
        }

        try {
            for (Object drawObject : draws) {
                GuiRendererDrawAccessor draw = (GuiRendererDrawAccessor) drawObject;
                StagedVertexBuffer.Draw stagedDraw = draw.mine2dengine$draw();
                List<Mine2DEffect> effects = mine2dengine$effectsByDraw.get(stagedDraw);
                if (effects == null || !Mine2DEffectContext.shouldRenderIn(effects, effect)) {
                    continue;
                }
                Mine2DEffect nestedEffect = mine2dengine$compositeByDraw.get(stagedDraw);
                if (nestedEffect != null) {
                    mine2dengine$prepareEffect(
                        nestedEffect,
                        target,
                        dynamicTransforms,
                        preparing
                    );
                }
            }

            GpuTexture source = target.getColorTexture();
            GpuTexture texture = RenderSystem.getDevice().createTexture(
                "Mine2D " + mine2dengine$effectLabel(effect) + " " + effect.id(),
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                source.getFormat(),
                source.getWidth(0),
                source.getHeight(0),
                1,
                1
            );
            GpuTextureView view = RenderSystem.getDevice().createTextureView(texture);
            EffectTarget effectTarget = new EffectTarget(texture, view);
            mine2dengine$effectTargets.put(effect, effectTarget);

            try (
                RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                        () -> "Mine2D " + mine2dengine$effectLabel(effect) + " " + effect.id(),
                        view,
                        Optional.of(new Vector4f(0.0F))
                    )
            ) {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                for (Object drawObject : draws) {
                    GuiRendererDrawAccessor draw = (GuiRendererDrawAccessor) drawObject;
                    StagedVertexBuffer.Draw stagedDraw = draw.mine2dengine$draw();
                    List<Mine2DEffect> effects = mine2dengine$effectsByDraw.get(stagedDraw);
                    if (effects != null && Mine2DEffectContext.shouldRenderIn(effects, effect)) {
                        mine2dengine$executeEffectMember(draw, renderPass);
                    }
                }
            } catch (RuntimeException | Error exception) {
                mine2dengine$effectTargets.remove(effect);
                effectTarget.close();
                throw exception;
            }
        } finally {
            preparing.remove(effect);
        }
    }

    @Unique
    private void mine2dengine$executeEffectMember(
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

        Mine2DEffect effect = mine2dengine$compositeByDraw.get(stagedDraw);
        if (effect != null) {
            EffectTarget target = mine2dengine$effectTargets.get(effect);
            if (target == null) {
                throw new IllegalStateException(
                    "Mine2D effect target was not prepared: " + effect
                );
            }
            renderPass.bindTexture(
                effect.kind() == Mine2DEffect.Kind.DROP_SHADOW
                    ? "DropShadowSampler"
                    : "ClipSampler",
                target.view(),
                RenderSystem.getSamplerCache().getClampToEdge(
                    effect.kind() == Mine2DEffect.Kind.DROP_SHADOW
                        ? FilterMode.LINEAR
                        : FilterMode.NEAREST
                )
            );
        }
    }

    @Unique
    private String mine2dengine$effectLabel(Mine2DEffect effect) {
        return switch (effect.kind()) {
            case DROP_SHADOW -> "drop-shadow mask";
            case ROUNDED_CLIP -> "rounded-clip layer";
        };
    }

    @Unique
    private void mine2dengine$closeEffectTargets() {
        mine2dengine$effectTargets.values().forEach(EffectTarget::close);
        mine2dengine$effectTargets.clear();
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
    private record EffectTarget(GpuTexture texture, GpuTextureView view) {
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
