package mihon.core.navigation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

// From https://github.com/adrielcafe/voyager/blob/main/voyager-core/src/commonMain/kotlin/cafe/adriel/voyager/core/stack/SnapshotStateStack.kt

fun <Item> List<Item>.toMutableStateStack(minSize: Int = 0): SnapshotStateStack<Item> =
    SnapshotStateStack(this, minSize)

fun <Item> mutableStateStackOf(vararg items: Item, minSize: Int = 0): SnapshotStateStack<Item> =
    SnapshotStateStack(*items, minSize = minSize)

@Composable
fun <Item : Any> rememberStateStack(vararg items: Item, minSize: Int = 0): SnapshotStateStack<Item> =
    rememberStateStack(items.toList(), minSize)

@Composable
fun <Item : Any> rememberStateStack(items: List<Item>, minSize: Int = 0): SnapshotStateStack<Item> =
    rememberSaveable(saver = stackSaver(minSize)) {
        SnapshotStateStack(items, minSize)
    }

enum class StackEvent {
    Push,
    Replace,
    Pop,
    Idle,
}

internal fun <T> MutableList<T>.removeLastElement(): T =
    if (isEmpty()) throw NoSuchElementException("List is empty.") else removeAt(lastIndex)

private fun <Item : Any> stackSaver(minSize: Int): Saver<SnapshotStateStack<Item>, Any> = listSaver(
    save = { stack -> stack.items },
    restore = { items -> SnapshotStateStack(items, minSize) },
)

class SnapshotStateStack<Item>(
    items: List<Item>,
    minSize: Int = 0,
) {
    public constructor(
        vararg items: Item,
        minSize: Int = 0,
    ) : this(
        items = items.toList(),
        minSize = minSize,
    )

    init {
        require(minSize >= 0) { "Min size $minSize is less than zero" }
        require(items.size >= minSize) { "Stack size ${items.size} is less than the min size $minSize" }
    }

    @PublishedApi
    internal val stateStack: SnapshotStateList<Item> = items.toMutableStateList()

    var lastEvent: StackEvent by mutableStateOf(StackEvent.Idle, neverEqualPolicy())
        private set

    val items: List<Item> by derivedStateOf {
        stateStack.toList()
    }

    val lastItemOrNull: Item? by derivedStateOf {
        stateStack.lastOrNull()
    }

    val size: Int by derivedStateOf {
        stateStack.size
    }

    val isEmpty: Boolean by derivedStateOf {
        stateStack.isEmpty()
    }

    val canPop: Boolean by derivedStateOf {
        stateStack.size > minSize
    }

    infix fun push(item: Item) {
        stateStack += item
        lastEvent = StackEvent.Push
    }

    infix fun push(items: List<Item>) {
        stateStack += items
        lastEvent = StackEvent.Push
    }

    infix fun replace(item: Item) {
        if (stateStack.isEmpty()) {
            push(item)
        } else {
            stateStack[stateStack.lastIndex] = item
        }
        lastEvent = StackEvent.Replace
    }

    infix fun replaceAll(item: Item) {
        Snapshot.withMutableSnapshot {
            stateStack.clear()
            stateStack += item
            lastEvent = StackEvent.Replace
        }
    }

    infix fun replaceAll(items: List<Item>) {
        Snapshot.withMutableSnapshot {
            stateStack.clear()
            stateStack += items
            lastEvent = StackEvent.Replace
        }
    }

    fun pop(): Boolean = if (canPop) {
        stateStack.removeLastElement()
        lastEvent = StackEvent.Pop
        true
    } else {
        false
    }

    fun popAll() {
        popUntil { false }
    }

    infix fun popUntil(predicate: (Item) -> Boolean): Boolean {
        var success = false
        val shouldPop = {
            lastItemOrNull
                ?.let(predicate)
                ?.also { success = it }
                ?.not()
                ?: false
        }

        while (canPop && shouldPop()) {
            stateStack.removeLastElement()
        }

        lastEvent = StackEvent.Pop

        return success
    }

    operator fun plusAssign(item: Item) {
        push(item)
    }

    operator fun plusAssign(items: List<Item>) {
        push(items)
    }

    fun clearEvent() {
        lastEvent = StackEvent.Idle
    }
}
