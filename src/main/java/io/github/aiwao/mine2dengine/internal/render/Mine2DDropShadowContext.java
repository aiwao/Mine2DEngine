package io.github.aiwao.mine2dengine.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks the drop-shadow groups to which extracted GUI render states belong. */
public final class Mine2DDropShadowContext {
    private static final AtomicLong NEXT_GROUP_ID = new AtomicLong();
    private static final ThreadLocal<List<Long>> GROUPS = new ThreadLocal<>();

    private Mine2DDropShadowContext() {
    }

    public static long nextGroupId() {
        return NEXT_GROUP_ID.incrementAndGet();
    }

    public static Scope beginGroup(long groupId) {
        List<Long> previous = GROUPS.get();
        ArrayList<Long> groups = new ArrayList<>(previous == null ? List.of() : previous);
        groups.add(groupId);
        GROUPS.set(List.copyOf(groups));
        return new Scope(previous);
    }

    public static Scope useGroups(List<Long> groups) {
        List<Long> previous = GROUPS.get();
        if (groups.isEmpty()) {
            GROUPS.remove();
        } else {
            GROUPS.set(List.copyOf(groups));
        }
        return new Scope(previous);
    }

    public static List<Long> currentGroups() {
        List<Long> groups = GROUPS.get();
        return groups == null ? List.of() : groups;
    }

    public static final class Scope implements AutoCloseable {
        private final List<Long> previous;
        private boolean closed;

        private Scope(List<Long> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                GROUPS.remove();
            } else {
                GROUPS.set(previous);
            }
        }
    }
}
