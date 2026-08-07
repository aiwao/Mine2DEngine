package io.github.aiwao.mine2dengine.internal;

import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Tracks only the glyph atlas textures owned by Mine2DFont instances. */
public final class LinearTextTextures {
    private static final Set<GpuTextureView> TEXTURES =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private LinearTextTextures() {
    }

    public static void register(GpuTextureView textureView) {
        TEXTURES.add(textureView);
    }

    public static void unregisterAll(Collection<GpuTextureView> textureViews) {
        synchronized (TEXTURES) {
            TEXTURES.removeAll(textureViews);
        }
    }

    public static boolean contains(GpuTextureView textureView) {
        return TEXTURES.contains(textureView);
    }
}
