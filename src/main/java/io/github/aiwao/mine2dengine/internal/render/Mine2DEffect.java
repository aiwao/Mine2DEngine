package io.github.aiwao.mine2dengine.internal.render;

import java.util.Objects;

/** Identifies one ordered offscreen GUI effect scope. */
public record Mine2DEffect(long id, Kind kind) {
    public enum Kind {
        DROP_SHADOW,
        ROUNDED_CLIP
    }

    public Mine2DEffect {
        if (id <= 0L) {
            throw new IllegalArgumentException("An effect id must be positive: " + id);
        }
        Objects.requireNonNull(kind, "kind");
    }
}
