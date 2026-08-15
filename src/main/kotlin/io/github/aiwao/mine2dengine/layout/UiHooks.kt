package io.github.aiwao.mine2dengine.layout

import java.util.Collections
import java.util.IdentityHashMap

/** The value snapshot and stable setter returned by [ComponentScope.useState]. */
class StateResult<T> internal constructor(
    val value: T,
    val setter: StateSetter<T>,
) {
    operator fun component1(): T = value

    operator fun component2(): StateSetter<T> = setter
}

/** Updates one state slot owned by a mounted component. */
class StateSetter<T> internal constructor(
    private val owner: ComponentInstance,
    private val slot: StateHookSlot<T>,
) {
    /** Replaces the current value. Equal values are ignored. */
    operator fun invoke(value: T) {
        set { value }
    }

    /** Computes a value from the latest slot value, including earlier updates in this batch. */
    fun update(transform: (T) -> T) {
        set { current -> transform(current) }
    }

    private inline fun set(nextValue: (T) -> T) {
        if (!owner.mounted) return
        owner.checkUiThread()
        check(!owner.isRuntimeRendering()) {
            "State cannot be updated while ${owner.displayName} is rendering"
        }
        val previous = slot.value
        val next = nextValue(previous)
        if (previous == next) return
        slot.value = next
        owner.markDirty()
    }
}

/** Receiver used while rendering one component mount. */
class ComponentScope internal constructor(
    private val instance: ComponentInstance,
) {
    /** Creates or reads the state slot at the current hook position. */
    fun <T> useState(initializer: () -> T): StateResult<T> = instance.useState(initializer)
}

internal sealed interface HookSlot

internal class StateHookSlot<T>(
    var value: T,
) : HookSlot {
    lateinit var setter: StateSetter<T>
}

internal class ComponentInstance(
    val definition: UiComponent<*>,
    val key: Any?,
    val parent: ComponentInstance?,
    var mountOrdinal: Int,
    initialContent: UiContent,
) {
    val hooks = mutableListOf<HookSlot>()
    val children: MutableSet<ComponentInstance> = Collections.newSetFromMap(IdentityHashMap())
    val uiThread: Thread = Thread.currentThread()
    var hookCursor: Int = 0
    var dirty: Boolean = false
    var mounted: Boolean = true
    var hasRendered: Boolean = false
    var rendering: Boolean = false
    var runtime: UiComponentRuntime? = null
    var committedContent: UiContent = initialContent
        private set
    private var pendingContent: UiContent? = null
    lateinit var mountedRoot: UiElement

    val displayName: String
        get() = definition.javaClass.simpleName.ifBlank { definition.toString() }

    fun render(content: UiContent = committedContent): UiElement {
        checkUiThread()
        check(!rendering) { "$displayName is already rendering" }
        hookCursor = 0
        rendering = true
        pendingContent = content
        val context = ComponentRenderContext(this)
        var contentBuilt = false
        val singleUseContent: UiContent = {
            check(!contentBuilt) { "Component content may only be built once per render" }
            contentBuilt = true
            content(this)
        }
        val root = try {
            ComponentRenderContext.with(context) {
                definition.renderWith(ComponentScope(this), singleUseContent)
            }
        } finally {
            rendering = false
        }
        if (hasRendered && hookCursor != hooks.size) {
            throw hookOrderError(
                index = hookCursor,
                expected = "${hooks.size} hooks",
                actual = "$hookCursor hooks",
            )
        }
        check(root.componentInstance == null || root.componentInstance === this) {
            "$displayName must return one host root element"
        }
        hasRendered = true
        root.componentInstance = this
        root.key = key
        root.componentStyleSheets = definition.styleSheets
        runtime?.didRender(this)
        return root
    }

    fun commitPendingRender() {
        pendingContent?.let { committedContent = it }
        pendingContent = null
    }

    fun rollbackPendingRender() {
        pendingContent = null
    }

    fun <T> useState(initializer: () -> T): StateResult<T> {
        check(rendering && ComponentRenderContext.current()?.instance === this) {
            "useState for $displayName may only be called while that component is rendering"
        }
        val index = hookCursor++
        if (index < hooks.size) {
            val slot = hooks[index]
            check(slot is StateHookSlot<*>) {
                hookOrderError(index, "StateHookSlot", slot.javaClass.simpleName)
            }
            @Suppress("UNCHECKED_CAST")
            slot as StateHookSlot<T>
            return StateResult(slot.value, slot.setter)
        }
        check(!hasRendered) {
            hookOrderError(index, "no additional hook", "StateHookSlot")
        }
        val slot = StateHookSlot(initializer())
        slot.setter = StateSetter(this, slot)
        hooks += slot
        return StateResult(slot.value, slot.setter)
    }

    fun checkUiThread() {
        check(Thread.currentThread() === uiThread) {
            "$displayName state may only be used on its UI thread (${uiThread.name})"
        }
    }

    fun isRuntimeRendering(): Boolean = runtime?.rendering == true ||
        ComponentRenderContext.current() != null

    fun markDirty() {
        if (dirty) return
        dirty = true
        runtime?.enqueue(this)
    }

    fun unmount() {
        if (!mounted) return
        mounted = false
        dirty = false
        rollbackPendingRender()
        children.toList().forEach(ComponentInstance::unmount)
        children.clear()
        parent?.children?.remove(this)
        runtime?.forget(this)
        runtime = null
    }

    private fun hookOrderError(index: Int, expected: String, actual: String): IllegalStateException =
        IllegalStateException(
            "$displayName changed hook order at index $index: expected $expected, got $actual",
        )
}

internal class ComponentRenderContext(
    val instance: ComponentInstance,
) {
    private val reusableChildren = instance.children.toList()
    private val claimed = Collections.newSetFromMap(IdentityHashMap<ComponentInstance, Boolean>())
    private var nextOrdinal: Int = 0
    var lastClaimedOrdinal: Int = 0
        private set

    fun claim(definition: UiComponent<*>, key: Any?): ComponentInstance? {
        val ordinal = nextOrdinal++
        lastClaimedOrdinal = ordinal
        val match = reusableChildren.firstOrNull { candidate ->
            candidate.mounted &&
                candidate !in claimed &&
                candidate.definition === definition &&
                if (key != null) candidate.key == key else
                    candidate.key == null && candidate.mountOrdinal == ordinal
        } ?: return null
        claimed += match
        match.mountOrdinal = ordinal
        return match
    }

    companion object {
        private val active = ThreadLocal<ComponentRenderContext?>()

        fun current(): ComponentRenderContext? = active.get()

        fun <T> with(context: ComponentRenderContext, block: () -> T): T {
            val previous = active.get()
            active.set(context)
            return try {
                block()
            } finally {
                active.set(previous)
            }
        }
    }
}
