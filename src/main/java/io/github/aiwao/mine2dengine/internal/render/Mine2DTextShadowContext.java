package io.github.aiwao.mine2dengine.internal.render;

/** Carries text-shadow extraction metadata into Minecraft's deferred glyph preparation. */
public final class Mine2DTextShadowContext {
    private static final ThreadLocal<Float> BLUR_RADIUS = new ThreadLocal<>();

    private Mine2DTextShadowContext() {
    }

    public static Scope begin(float blurRadius) {
        Float previous = BLUR_RADIUS.get();
        BLUR_RADIUS.set(blurRadius);
        return new Scope(previous);
    }

    public static Float currentBlurRadius() {
        return BLUR_RADIUS.get();
    }

    public static final class Scope implements AutoCloseable {
        private final Float previous;
        private boolean closed;

        private Scope(Float previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                BLUR_RADIUS.remove();
            } else {
                BLUR_RADIUS.set(previous);
            }
        }
    }
}
