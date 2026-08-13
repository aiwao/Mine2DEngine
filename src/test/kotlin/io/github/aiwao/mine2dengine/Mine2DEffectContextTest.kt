package io.github.aiwao.mine2dengine

import io.github.aiwao.mine2dengine.internal.render.Mine2DEffect
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffectContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Mine2DEffectContextTest {
    @Test
    fun `nested effects preserve order and restore the context`() {
        val outer = Mine2DEffect(10L, Mine2DEffect.Kind.ROUNDED_CLIP)
        val inner = Mine2DEffect(20L, Mine2DEffect.Kind.DROP_SHADOW)
        assertEquals(emptyList(), Mine2DEffectContext.currentEffects())

        Mine2DEffectContext.beginEffect(outer).use {
            assertEquals(listOf(outer), Mine2DEffectContext.currentEffects())
            Mine2DEffectContext.beginEffect(inner).use {
                assertEquals(listOf(outer, inner), Mine2DEffectContext.currentEffects())
            }
            assertEquals(listOf(outer), Mine2DEffectContext.currentEffects())
        }

        assertEquals(emptyList(), Mine2DEffectContext.currentEffects())
    }

    @Test
    fun `explicit deferred paths and captured paths are immutable`() {
        val clip = Mine2DEffect(1L, Mine2DEffect.Kind.ROUNDED_CLIP)
        val shadow = Mine2DEffect(2L, Mine2DEffect.Kind.DROP_SHADOW)
        val effects = mutableListOf(clip, shadow)
        val state = Any()

        Mine2DEffectContext.useEffects(effects).use {
            Mine2DEffectContext.capture(state)
            effects.removeLast()
            assertEquals(listOf(clip, shadow), Mine2DEffectContext.currentEffects())
            assertEquals(listOf(clip, shadow), Mine2DEffectContext.capturedEffects(state))
        }
        Mine2DEffectContext.release(state)

        assertEquals(emptyList(), Mine2DEffectContext.currentEffects())
        assertEquals(emptyList(), Mine2DEffectContext.capturedEffects(state))
    }

    @Test
    fun `inner clips replace their member draws while shadows remain additive`() {
        val outerShadow = Mine2DEffect(1L, Mine2DEffect.Kind.DROP_SHADOW)
        val innerClip = Mine2DEffect(2L, Mine2DEffect.Kind.ROUNDED_CLIP)
        val innerShadow = Mine2DEffect(3L, Mine2DEffect.Kind.DROP_SHADOW)

        assertFalse(
            Mine2DEffectContext.shouldRenderIn(listOf(outerShadow, innerClip), outerShadow),
        )
        assertTrue(
            Mine2DEffectContext.shouldRenderIn(listOf(outerShadow, innerClip), innerClip),
        )
        assertTrue(
            Mine2DEffectContext.shouldRenderIn(listOf(innerClip, innerShadow), innerClip),
        )
        assertTrue(Mine2DEffectContext.containsRoundedClip(listOf(outerShadow, innerClip)))
        assertFalse(Mine2DEffectContext.containsRoundedClip(listOf(outerShadow, innerShadow)))
    }
}
