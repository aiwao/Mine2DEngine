package io.github.aiwao.mine2dengine

import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowContext
import kotlin.test.Test
import kotlin.test.assertEquals

class Mine2DDropShadowContextTest {
    @Test
    fun `nested groups preserve outer membership and restore the context`() {
        assertEquals(emptyList(), Mine2DDropShadowContext.currentGroups())

        Mine2DDropShadowContext.beginGroup(10L).use {
            assertEquals(listOf(10L), Mine2DDropShadowContext.currentGroups())
            Mine2DDropShadowContext.beginGroup(20L).use {
                assertEquals(listOf(10L, 20L), Mine2DDropShadowContext.currentGroups())
            }
            assertEquals(listOf(10L), Mine2DDropShadowContext.currentGroups())
        }

        assertEquals(emptyList(), Mine2DDropShadowContext.currentGroups())
    }

    @Test
    fun `explicit deferred groups are immutable and restore the context`() {
        val groups = mutableListOf(1L, 2L)
        Mine2DDropShadowContext.useGroups(groups).use {
            groups += 3L
            assertEquals(listOf(1L, 2L), Mine2DDropShadowContext.currentGroups())
        }

        assertEquals(emptyList(), Mine2DDropShadowContext.currentGroups())
    }
}
