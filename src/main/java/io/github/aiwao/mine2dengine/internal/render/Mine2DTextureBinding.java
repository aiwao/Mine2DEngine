package io.github.aiwao.mine2dengine.internal.render;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.Objects;

/** Internal immutable description of one texture and sampler binding. */
public record Mine2DTextureBinding(
    String name,
    Kind kind,
    GpuTextureView texture,
    GpuSampler sampler,
    FilterMode filterMode
) {
    public enum Kind {
        FIXED,
        GUI_BACKGROUND
    }

    public Mine2DTextureBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");

        switch (kind) {
            case FIXED -> {
                Objects.requireNonNull(texture, "texture");
                Objects.requireNonNull(sampler, "sampler");
                if (filterMode != null) {
                    throw new IllegalArgumentException("A fixed texture binding cannot specify a filter mode");
                }
            }
            case GUI_BACKGROUND -> {
                Objects.requireNonNull(filterMode, "filterMode");
                if (texture != null || sampler != null) {
                    throw new IllegalArgumentException(
                        "A GUI background binding is resolved by the renderer and cannot contain a fixed texture"
                    );
                }
            }
        }
    }

    public static Mine2DTextureBinding fixed(
        String name,
        GpuTextureView texture,
        GpuSampler sampler
    ) {
        return new Mine2DTextureBinding(name, Kind.FIXED, texture, sampler, null);
    }

    public static Mine2DTextureBinding guiBackground(String name, FilterMode filterMode) {
        return new Mine2DTextureBinding(name, Kind.GUI_BACKGROUND, null, null, filterMode);
    }
}
