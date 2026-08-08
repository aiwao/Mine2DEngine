package io.github.aiwao.mine2dengine.internal.render;

import java.util.Objects;

/** Internal immutable CPU snapshot of one uniform block. */
public record Mine2DUniformBinding(String name, byte[] data) {
    public Mine2DUniformBinding {
        Objects.requireNonNull(name, "name");
        data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public byte[] dataUnsafe() {
        return data;
    }
}
