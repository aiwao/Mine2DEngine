package io.github.aiwao.mine2dengine.internal.render;

import java.util.List;

/** Identifies GUI draws whose composited alpha contributes to one or more drop shadows. */
public interface Mine2DDropShadowMemberRenderState {
    List<Long> mine2dengineDropShadowGroups();
}
