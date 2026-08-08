package io.github.aiwao.mine2dengine.internal.render;

/** Implemented by GUI render states which require Mine2D material bindings. */
public interface Mine2DMaterialRenderState {
    Mine2DRenderBindings mine2dengineBindings();
}
