package io.github.aiwao.mine2dengine.internal.render;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks ordered effect scopes while GUI render states are extracted and prepared. */
public final class Mine2DEffectContext {
    private static final AtomicLong NEXT_EFFECT_ID = new AtomicLong();
    private static final ThreadLocal<List<Mine2DEffect>> EFFECTS = new ThreadLocal<>();
    private static final Map<Object, List<Mine2DEffect>> CAPTURED_EFFECTS =
        Collections.synchronizedMap(new IdentityHashMap<>());

    private Mine2DEffectContext() {
    }

    public static Mine2DEffect nextEffect(Mine2DEffect.Kind kind) {
        return new Mine2DEffect(NEXT_EFFECT_ID.incrementAndGet(), kind);
    }

    public static Scope beginEffect(Mine2DEffect effect) {
        List<Mine2DEffect> previous = EFFECTS.get();
        List<Mine2DEffect> effects = new java.util.ArrayList<>(
            previous == null ? List.of() : previous
        );
        effects.add(effect);
        EFFECTS.set(List.copyOf(effects));
        return new Scope(previous);
    }

    public static Scope useEffects(List<Mine2DEffect> effects) {
        List<Mine2DEffect> previous = EFFECTS.get();
        if (effects.isEmpty()) {
            EFFECTS.remove();
        } else {
            EFFECTS.set(List.copyOf(effects));
        }
        return new Scope(previous);
    }

    public static List<Mine2DEffect> currentEffects() {
        List<Mine2DEffect> effects = EFFECTS.get();
        return effects == null ? List.of() : effects;
    }

    /** Captures the current path for a deferred render state by object identity. */
    public static void capture(Object renderState) {
        List<Mine2DEffect> effects = currentEffects();
        if (effects.isEmpty()) {
            CAPTURED_EFFECTS.remove(renderState);
        } else {
            CAPTURED_EFFECTS.put(renderState, effects);
        }
    }

    public static List<Mine2DEffect> capturedEffects(Object renderState) {
        List<Mine2DEffect> effects = CAPTURED_EFFECTS.get(renderState);
        return effects == null ? List.of() : effects;
    }

    public static void release(Object renderState) {
        CAPTURED_EFFECTS.remove(renderState);
    }

    public static void clearCapturedEffects() {
        CAPTURED_EFFECTS.clear();
    }

    public static boolean containsRoundedClip(List<Mine2DEffect> effects) {
        return effects.stream().anyMatch(
            effect -> effect.kind() == Mine2DEffect.Kind.ROUNDED_CLIP
        );
    }

    /** Returns whether a draw belongs directly to an effect after inner clips are composited. */
    public static boolean shouldRenderIn(
        List<Mine2DEffect> effects,
        Mine2DEffect target
    ) {
        int targetIndex = effects.indexOf(target);
        if (targetIndex < 0) {
            return false;
        }
        for (int index = targetIndex + 1; index < effects.size(); index++) {
            if (effects.get(index).kind() == Mine2DEffect.Kind.ROUNDED_CLIP) {
                return false;
            }
        }
        return true;
    }

    public static final class Scope implements AutoCloseable {
        private final List<Mine2DEffect> previous;
        private boolean closed;

        private Scope(List<Mine2DEffect> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                EFFECTS.remove();
            } else {
                EFFECTS.set(previous);
            }
        }
    }
}
