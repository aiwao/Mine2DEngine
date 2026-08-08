package io.github.aiwao.mine2dengine.internal.render;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Objects;

/** Internal immutable snapshot of one texture and sampler binding. */
public record Mine2DTextureBinding(String name, GpuTextureView texture, GpuSampler sampler) {
    public Mine2DTextureBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(sampler, "sampler");
    }
}
