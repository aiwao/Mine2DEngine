package io.github.aiwao.mine2dengine.internal.render;

import java.util.List;

/** Internal immutable collection of all bindings required by one draw. */
public record Mine2DRenderBindings(
    List<Mine2DUniformBinding> uniforms,
    List<Mine2DTextureBinding> textures
) {
    public static final Mine2DRenderBindings EMPTY = new Mine2DRenderBindings(List.of(), List.of());

    public Mine2DRenderBindings {
        uniforms = List.copyOf(uniforms);
        textures = List.copyOf(textures);
    }

    public boolean isEmpty() {
        return uniforms.isEmpty() && textures.isEmpty();
    }
}
