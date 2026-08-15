package io.github.aiwao.mine2dengine.layout

import java.util.Collections
import java.util.IdentityHashMap

/** Owns mounted components and batches their hook-driven commits for one [UiLayout]. */
internal class UiComponentRuntime(
    initialRoot: UiElement,
) {
    var committedRoot: UiElement = initialRoot
        private set

    private val instances: MutableSet<ComponentInstance> =
        Collections.newSetFromMap(IdentityHashMap())
    private val dirty = linkedSetOf<ComponentInstance>()
    private val renderedThisFlush: MutableSet<ComponentInstance> =
        Collections.newSetFromMap(IdentityHashMap())
    private lateinit var layout: UiLayout
    private var eventDepth: Int = 0
    private var flushing: Boolean = false
    var rendering: Boolean = false
        private set

    fun attach(layout: UiLayout) {
        this.layout = layout
        validateUniqueKeys(committedRoot)
        synchronizeInstances()
    }

    fun enqueue(instance: ComponentInstance) {
        if (instance.mounted) dirty += instance
    }

    fun didRender(instance: ComponentInstance) {
        if (flushing) renderedThisFlush += instance
    }

    fun forget(instance: ComponentInstance) {
        dirty -= instance
        instances -= instance
    }

    fun beginEvent() {
        eventDepth += 1
    }

    fun endEvent() {
        check(eventDepth > 0) { "Unbalanced UI event dispatch" }
        eventDepth -= 1
        if (eventDepth == 0) flushUpdates()
    }

    fun checkNotRendering(operation: String) {
        check(!rendering) { "$operation cannot be called while a component is rendering" }
    }

    fun flushUpdates(): Boolean {
        if (eventDepth > 0 || flushing || dirty.isEmpty()) return false
        flushing = true
        renderedThisFlush.clear()
        val transaction = ReconcileTransaction(this)
        val work = dirty
            .filter(ComponentInstance::mounted)
            .sortedBy(::depthOf)
        dirty.removeAll(work.toSet())
        try {
            rendering = true
            work.forEach { instance ->
                if (
                    !instance.mounted ||
                    instance in renderedThisFlush ||
                    transaction.isScheduledForRemoval(instance)
                ) {
                    return@forEach
                }
                val nextRoot = instance.render()
                validateUniqueKeys(nextRoot)
                transaction.reconcileComponent(instance, nextRoot)
            }
            rendering = false
            layout.recalculateForStateUpdate()
            transaction.commit()
            renderedThisFlush.forEach { instance ->
                instance.commitPendingRender()
                instance.dirty = false
                dirty -= instance
            }
            synchronizeInstances()
        } catch (failure: Throwable) {
            rendering = false
            transaction.rollback()
            renderedThisFlush.forEach(ComponentInstance::rollbackPendingRender)
            work.forEach(ComponentInstance::rollbackPendingRender)
            discardUncommittedInstances()
            work.forEach { instance -> instance.dirty = false }
            throw failure
        } finally {
            rendering = false
            flushing = false
            renderedThisFlush.clear()
        }
        return true
    }

    internal fun replaceRoot(previous: UiElement, next: UiElement) {
        if (committedRoot === previous) committedRoot = next
    }

    internal fun restoreRoot(root: UiElement) {
        committedRoot = root
    }

    private fun synchronizeInstances() {
        val mountedNow = collectMountedInstances()

        instances.filterNot(mountedNow::contains).toList().forEach(ComponentInstance::unmount)
        mountedNow.forEach { instance ->
            check(instance.runtime == null || instance.runtime === this) {
                "A component mount cannot belong to more than one UiLayout"
            }
            instance.runtime = this
            instances += instance
            if (instance.dirty) enqueue(instance)
        }
    }

    private fun discardUncommittedInstances() {
        val mountedNow = collectMountedInstances()
        mountedNow.forEach { parent ->
            parent.children
                .filterNot(mountedNow::contains)
                .toList()
                .forEach(ComponentInstance::unmount)
        }
    }

    private fun collectMountedInstances(): MutableSet<ComponentInstance> {
        val mountedNow: MutableSet<ComponentInstance> =
            Collections.newSetFromMap(IdentityHashMap())
        fun visit(element: UiElement) {
            element.componentInstance?.let { mountedNow += it }
            (element as? UiContainer)?.children?.forEach(::visit)
        }
        visit(committedRoot)
        return mountedNow
    }

    private fun depthOf(instance: ComponentInstance): Int {
        var depth = 0
        var parent = instance.parent
        while (parent != null) {
            depth += 1
            parent = parent.parent
        }
        return depth
    }
}

private class ReconcileTransaction(
    private val runtime: UiComponentRuntime,
) {
    private val undo = mutableListOf<() -> Unit>()
    private val afterCommit = mutableListOf<() -> Unit>()
    private val scheduledForRemoval: MutableSet<ComponentInstance> =
        Collections.newSetFromMap(IdentityHashMap())

    fun reconcileComponent(instance: ComponentInstance, nextRoot: UiElement) {
        val previousRoot = instance.mountedRoot
        val previousRuntimeRoot = runtime.committedRoot
        val reconciled = reconcile(previousRoot, nextRoot)
        if (reconciled !== previousRoot) {
            val parentReplacement = replaceChildReference(
                root = runtime.committedRoot,
                previous = previousRoot,
                next = reconciled,
            )
            runtime.replaceRoot(previousRoot, reconciled)
            undo += {
                parentReplacement?.let { (parent, index) ->
                    parent.children[index] = previousRoot
                }
                runtime.restoreRoot(previousRuntimeRoot)
            }
        }
    }

    fun isScheduledForRemoval(instance: ComponentInstance): Boolean {
        var current: ComponentInstance? = instance
        while (current != null) {
            if (current in scheduledForRemoval) return true
            current = current.parent
        }
        return false
    }

    fun commit() {
        scheduledForRemoval.toList().forEach(ComponentInstance::unmount)
        afterCommit.forEach { action -> action() }
        afterCommit.clear()
        undo.clear()
    }

    fun rollback() {
        undo.asReversed().forEach { restore -> restore() }
        undo.clear()
        afterCommit.clear()
        scheduledForRemoval.clear()
    }

    private fun reconcile(previous: UiElement, next: UiElement): UiElement {
        val reconciled = reconcileNode(previous, next)
        val previousComponent = previous.componentInstance
        if (
            previousComponent != null &&
            previousComponent === next.componentInstance &&
            reconciled !== previous &&
            previousComponent.mountedRoot === previous
        ) {
            previousComponent.mountedRoot = reconciled
            undo += { previousComponent.mountedRoot = previous }
        }
        return reconciled
    }

    private fun reconcileNode(previous: UiElement, next: UiElement): UiElement {
        if (previous === next) return previous

        val previousComponent = previous.componentInstance
        val nextComponent = next.componentInstance
        if (previousComponent != null || nextComponent != null) {
            if (
                previousComponent == null ||
                nextComponent == null ||
                previousComponent.definition !== nextComponent.definition ||
                previousComponent.key != nextComponent.key
            ) {
                scheduleUnmount(previous)
                return next
            }
            if (previousComponent !== nextComponent) {
                nextComponent.unmount()
                return previous
            }
        }

        if (!sameHostIdentity(previous, next)) {
            scheduleUnmount(previous, excluding = previousComponent)
            return next
        }

        patchCommon(previous, next)
        if (previous is InputControl && next is InputControl) {
            patchInputControl(previous, next)
        }
        when {
            previous is Paragraph && next is Paragraph -> patchParagraph(previous, next)
            previous is TextInput && next is TextInput -> patchTextInput(previous, next)
            previous is ColorInput && next is ColorInput -> patchColorInput(previous, next)
            previous is RangeInput<*> && next is RangeInput<*> -> patchRangeInput(previous, next)
        }
        if (previous is UiContainer && next is UiContainer) {
            reconcileChildren(previous, next)
        }
        return previous
    }

    private fun reconcileChildren(previous: UiContainer, next: UiContainer) {
        validateSiblingKeys(next.children)
        val oldChildren = previous.children.toList()
        val oldByKey = oldChildren.filter { it.key != null }.associateBy(UiElement::key)
        val used: MutableSet<UiElement> = Collections.newSetFromMap(IdentityHashMap())
        val reconciled = next.children.mapIndexed { index, nextChild ->
            val candidate = if (nextChild.key != null) {
                oldByKey[nextChild.key]
            } else {
                oldChildren.getOrNull(index)
                    ?.takeIf { old -> old.key == null && sameElementIdentity(old, nextChild) }
                    ?: nextChild.componentInstance?.let { nextInstance ->
                        oldChildren.firstOrNull { old ->
                            old !in used && old.componentInstance === nextInstance
                        }
                    }
            }
            if (candidate != null && sameElementIdentity(candidate, nextChild)) {
                used += candidate
                reconcile(candidate, nextChild)
            } else {
                nextChild
            }
        }
        oldChildren.filterNot(used::contains).forEach(::scheduleUnmount)

        val previousChildren = previous.children.toList()
        val previousSheets = previous.styleSheets.toList()
        undo += {
            previous.children.clear()
            previous.children.addAll(previousChildren)
            previous.styleSheets.clear()
            previous.styleSheets.addAll(previousSheets)
        }
        previous.children.clear()
        previous.children.addAll(reconciled)
        previous.styleSheets.clear()
        previous.styleSheets.addAll(next.styleSheets)
    }

    private fun patchCommon(previous: UiElement, next: UiElement) {
        val oldStyle = previous.styleProviderSnapshot()
        val oldOnClick = previous.onClick
        val oldOnMouseMove = previous.onMouseMove
        val oldOnDrag = previous.onDrag
        val oldOnMouseOver = previous.onMouseOver
        val oldOnMouseOut = previous.onMouseOut
        val oldDisabled = previous.disabled
        val oldSheets = previous.componentStyleSheets
        undo += {
            previous.restoreStyleProvider(oldStyle)
            previous.onClick = oldOnClick
            previous.onMouseMove = oldOnMouseMove
            previous.onDrag = oldOnDrag
            previous.onMouseOver = oldOnMouseOver
            previous.onMouseOut = oldOnMouseOut
            previous.disabled = oldDisabled
            previous.componentStyleSheets = oldSheets
        }
        previous.copyStyleProviderFrom(next)
        previous.onClick = next.onClick
        previous.onMouseMove = next.onMouseMove
        previous.onDrag = next.onDrag
        previous.onMouseOver = next.onMouseOver
        previous.onMouseOut = next.onMouseOut
        previous.disabled = next.disabled
        previous.componentStyleSheets = next.componentStyleSheets
    }

    private fun patchInputControl(previous: InputControl, next: InputControl) {
        val oldOnKeyPressed = previous.onKeyPressed
        undo += { previous.onKeyPressed = oldOnKeyPressed }
        previous.onKeyPressed = next.onKeyPressed
    }

    private fun patchParagraph(previous: Paragraph, next: Paragraph) {
        val oldText = previous.text
        undo += { previous.text = oldText }
        previous.text = next.text
    }

    private fun patchTextInput(previous: TextInput, next: TextInput) {
        val oldValue = previous.value
        val oldValueControlled = previous.valueControlled
        val oldPlaceholder = previous.placeholder
        val oldMaxLength = previous.maxLength
        val oldSize = previous.size
        val oldReadOnly = previous.readOnly
        val oldOnInput = previous.onInput
        val oldOnChange = previous.onChange
        val oldOnFocus = previous.onFocus
        val oldOnBlur = previous.onBlur
        undo += {
            previous.placeholder = oldPlaceholder
            if (previous.maxLength != oldMaxLength) previous.maxLength = oldMaxLength
            previous.size = oldSize
            previous.readOnly = oldReadOnly
            previous.onInput = oldOnInput
            previous.onChange = oldOnChange
            previous.onFocus = oldOnFocus
            previous.onBlur = oldOnBlur
            previous.valueControlled = oldValueControlled
        }
        previous.placeholder = next.placeholder
        if (previous.maxLength != next.maxLength) previous.maxLength = next.maxLength
        if (previous.size != next.size) previous.size = next.size
        previous.readOnly = next.readOnly
        previous.onInput = next.onInput
        previous.onChange = next.onChange
        previous.onFocus = next.onFocus
        previous.onBlur = next.onBlur
        previous.valueControlled = next.valueControlled
        if (next.valueControlled && oldValue != next.value) {
            afterCommit += { previous.value = next.value }
        }
    }

    private fun patchColorInput(previous: ColorInput, next: ColorInput) {
        val oldValue = previous.value
        val oldValueControlled = previous.valueControlled
        val oldLabel = previous.label
        val oldOnInput = previous.onInput
        val oldOnChange = previous.onChange
        val oldOnFocus = previous.onFocus
        val oldOnBlur = previous.onBlur
        undo += {
            previous.label = oldLabel
            previous.onInput = oldOnInput
            previous.onChange = oldOnChange
            previous.onFocus = oldOnFocus
            previous.onBlur = oldOnBlur
            previous.valueControlled = oldValueControlled
        }
        previous.label = next.label
        previous.onInput = next.onInput
        previous.onChange = next.onChange
        previous.onFocus = next.onFocus
        previous.onBlur = next.onBlur
        previous.valueControlled = next.valueControlled
        if (next.valueControlled && oldValue != next.value) {
            afterCommit += { previous.value = next.value }
        }
    }

    private fun patchRangeInput(previous: RangeInput<*>, next: RangeInput<*>) {
        previous.patchConfigurationFrom(next, undo, afterCommit)
    }

    private fun scheduleUnmount(element: UiElement, excluding: ComponentInstance? = null) {
        element.componentInstance?.takeIf { it !== excluding }?.let { instance ->
            scheduledForRemoval += instance
            return
        }
        (element as? UiContainer)?.children?.forEach { child ->
            scheduleUnmount(child, excluding)
        }
    }

    private fun replaceChildReference(
        root: UiElement,
        previous: UiElement,
        next: UiElement,
    ): Pair<UiContainer, Int>? {
        val container = root as? UiContainer ?: return null
        container.children.forEachIndexed { index, child ->
            if (child === previous) {
                container.children[index] = next
                return container to index
            }
            replaceChildReference(child, previous, next)?.let { return it }
        }
        return null
    }
}

private fun sameElementIdentity(previous: UiElement, next: UiElement): Boolean {
    val previousComponent = previous.componentInstance
    val nextComponent = next.componentInstance
    if (previousComponent != null || nextComponent != null) {
        return previousComponent != null &&
            nextComponent != null &&
            previousComponent.definition === nextComponent.definition &&
            previousComponent.key == nextComponent.key
    }
    return sameHostIdentity(previous, next)
}

private fun sameHostIdentity(previous: UiElement, next: UiElement): Boolean =
    previous.javaClass === next.javaClass &&
        previous.tag == next.tag &&
        previous.id == next.id &&
        previous.className == next.className &&
        (previous !is RangeInput<*> ||
            next !is RangeInput<*> ||
            previous.numberType === next.numberType)

internal fun validateUniqueKeys(root: UiElement) {
    if (root !is UiContainer) return
    validateSiblingKeys(root.children)
    root.children.forEach(::validateUniqueKeys)
}

private fun validateSiblingKeys(children: List<UiElement>) {
    val keys = mutableSetOf<Any?>()
    children.forEach { child ->
        val key = child.key ?: return@forEach
        check(keys.add(key)) { "Duplicate component or element key among siblings: $key" }
    }
}
