package exh.util

import android.util.SparseArray
import java.util.*

class NakedTrieNode<T>(val key: Int, var parent: NakedTrieNode<T>?) {
    val children = SparseArray<NakedTrieNode<T>>(1)
    var hasData: Boolean = false
    var data: T? = null

    // Walks in ascending order
    // Consumer should return true to continue walking, false to stop walking
    inline fun walk(prefix: String, consumer: (String, T) -> Boolean, leavesOnly: Boolean) {
        // Special case root
        if(hasData && (!leavesOnly || children.size() <= 0)) {
            if(!consumer(prefix, data as T)) return
        }

        val stack = LinkedList<Pair<String, NakedTrieNode<T>>>()
        SparseArrayValueCollection(children, true).forEach {
            stack += prefix + it.key.toChar() to it
        }
        while(!stack.isEmpty()) {
            val (key, bottom) = stack.removeLast()
            SparseArrayValueCollection(bottom.children, true).forEach {
                stack += key + it.key.toChar() to it
            }
            if(bottom.hasData && (!leavesOnly || bottom.children.size() <= 0)) {
                if(!consumer(key, bottom.data as T)) return
            }
        }
    }

    fun getAsNode(key: String): NakedTrieNode<T>? {
        var current = this
        for(c in key) {
            current = current.children.get(c.toInt()) ?: return null
            if(!current.hasData) return null
        }
        return current
    }
}

/**
 * Fast, memory efficient and flexible trie implementation with implementation details exposed
 */
class NakedTrie<T> : MutableMap<String, T> {
    /**
     * Returns the number of key/value pairs in the map.
     */
    override var size: Int = 0
        private set

    /**
     * Returns `true` if the map is empty (contains no elements), `false` otherwise.
     */
    override fun isEmpty() = size <= 0

    /**
     * Removes all elements from this map.
     */
    override fun clear() {
        root.children.clear()
        root.hasData = false
        root.data = null
        size = 0
    }

    val root = NakedTrieNode<T>(-1, null)
    private var version: Long = 0

    override fun put(key: String, value: T): T? {
        // Traverse to node location in tree, making parent nodes if required
        var current = root
        for(c in key) {
            val castedC = c.toInt()
            var node = current.children.get(castedC)
            if(node == null) {
                node = NakedTrieNode(castedC, current)
                current.children.put(castedC, node)
            }
            current = node
        }

        // Add data to node or replace existing data
        val previous = if(current.hasData) {
            current.data
        } else {
            current.hasData = true
            size++
            null
        }
        current.data = value

        version++

        return previous
    }

    override fun get(key: String): T? {
        val current = getAsNode(key) ?: return null
        return if(current.hasData) current.data else null
    }

    fun getAsNode(key: String): NakedTrieNode<T>? {
        return root.getAsNode(key)
    }

    override fun containsKey(key: String): Boolean {
        var current = root
        for(c in key) {
            current = current.children.get(c.toInt()) ?: return false
            if(!current.hasData) return false
        }
        return current.hasData
    }

    /**
     * Removes the specified key and its corresponding value from this map.
     *
     * @return the previous value associated with the key, or `null` if the key was not present in the map.
     */
    override fun remove(key: String): T? {
        // Traverse node tree while keeping track of the nodes we have visited
        val nodeStack = LinkedList<NakedTrieNode<T>>()
        for(c in key) {
            val bottomOfStack = nodeStack.last
            val current = bottomOfStack.children.get(c.toInt()) ?: return null
            if(!current.hasData) return null
            nodeStack.add(bottomOfStack)
        }

        // Mark node as having no data
        val bottomOfStack = nodeStack.last
        bottomOfStack.hasData = false
        val oldData = bottomOfStack.data
        bottomOfStack.data = null // Clear data field for GC

        // Remove nodes that we visited that are useless
        for(curBottom in nodeStack.descendingIterator()) {
            val parent = curBottom.parent ?: break
            if(!curBottom.hasData && curBottom.children.size() <= 0) {
                // No data or child nodes, this node is useless, discard
                parent.children.remove(curBottom.key)
            } else break
        }

        version++
        size--

        return oldData
    }

    /**
     * Updates this map with key/value pairs from the specified map [from].
     */
    override fun putAll(from: Map<out String, T>) {
        // No way to optimize this so yeah...
        from.forEach { (s, u) ->
            put(s, u)
        }
    }

    // Walks in ascending order
    // Consumer should return true to continue walking, false to stop walking
    inline fun walk(consumer: (String, T) -> Boolean) {
        walk(consumer, false)
    }

    // Walks in ascending order
    // Consumer should return true to continue walking, false to stop walking
    inline fun walk(consumer: (String, T) -> Boolean, leavesOnly: Boolean) {
        root.walk("", consumer, leavesOnly)
    }

    fun getOrPut(key: String, producer: () -> T): T {
        // Traverse to node location in tree, making parent nodes if required
        var current = root
        for(c in key) {
            val castedC = c.toInt()
            var node = current.children.get(castedC)
            if(node == null) {
                node = NakedTrieNode(castedC, current)
                current.children.put(castedC, node)
            }
            current = node
        }

        // Add data to node or replace existing data
        if(!current.hasData) {
            current.hasData = true
            current.data = producer()
            size++
            version++
        }

        return current.data as T
    }

    // Includes root
    fun subMap(prefix: String, leavesOnly: Boolean = false): Map<String, T> {
        val node = getAsNode(prefix) ?: return emptyMap()

        return object : Map<String, T> {
            /**
             * Returns a read-only [Set] of all key/value pairs in this map.
             */
            override val entries: Set<Map.Entry<String, T>>
                get() {
                    val out = mutableSetOf<Map.Entry<String, T>>()
                    node.walk("", { k, v ->
                        out.add(AbstractMap.SimpleImmutableEntry(k, v))
                        true
                    }, leavesOnly)
                    return out
                }
            /**
             * Returns a read-only [Set] of all keys in this map.
             */
            override val keys: Set<String>
                get() {
                    val out = mutableSetOf<String>()
                    node.walk("", { k, _ ->
                        out.add(k)
                        true
                    }, leavesOnly)
                    return out
                }

            /**
             * Returns the number of key/value pairs in the map.
             */
            override val size: Int get() {
                var s = 0
                node.walk("", { _, _ -> s++; true }, leavesOnly)
                return s
            }

            /**
             * Returns a read-only [Collection] of all values in this map. Note that this collection may contain duplicate values.
             */
            override val values: Collection<T>
                get() {
                    val out = mutableSetOf<T>()
                    node.walk("", { _, v ->
                        out.add(v)
                        true
                    }, leavesOnly)
                    return out
                }

            /**
             * Returns `true` if the map contains the specified [key].
             */
            override fun containsKey(key: String): Boolean {
                if(!key.startsWith(prefix)) return false

                val childNode = node.getAsNode(key.removePrefix(prefix)) ?: return false
                return childNode.hasData && (!leavesOnly || childNode.children.size() <= 0)
            }

            /**
             * Returns `true` if the map maps one or more keys to the specified [value].
             */
            override fun containsValue(value: T): Boolean {
                node.walk("", { _, v ->
                    if(v == value) return true
                    true
                }, leavesOnly)
                return false
            }

            /**
             * Returns the value corresponding to the given [key], or `null` if such a key is not present in the map.
             */
            override fun get(key: String): T? {
                if(!key.startsWith(prefix)) return null

                val childNode = node.getAsNode(key.removePrefix(prefix)) ?: return null
                if(!childNode.hasData || (leavesOnly && childNode.children.size() > 0)) return null
                return childNode.data
            }

            /**
             * Returns `true` if the map is empty (contains no elements), `false` otherwise.
             */
            override fun isEmpty(): Boolean {
                if(node.children.size() <= 0 && !root.hasData) return true
                if(!leavesOnly) return false
                node.walk("", { _, _ -> return false }, leavesOnly)
                return true
            }
        }
    }

    // Slow methods below

    /**
     * Returns `true` if the map maps one or more keys to the specified [value].
     */
    override fun containsValue(value: T): Boolean {
        walk { _, t ->
            if(t == value) {
                return true
            }

            true
        }

        return false
    }

    /**
     * Returns a [MutableSet] of all key/value pairs in this map.
     */
    override val entries: MutableSet<MutableMap.MutableEntry<String, T>>
        get() = FakeMutableSet.fromSet(mutableSetOf<MutableMap.MutableEntry<String, T>>().apply {
            walk { k, v ->
                this += FakeMutableEntry.fromPair(k, v)
                true
            }
        })

    /**
     * Returns a [MutableSet] of all keys in this map.
     */
    override val keys: MutableSet<String>
        get() = FakeMutableSet.fromSet(mutableSetOf<String>().apply {
            walk { k, _ ->
                this += k
                true
            }
        })

    /**
     * Returns a [MutableCollection] of all values in this map. Note that this collection may contain duplicate values.
     */
    override val values: MutableCollection<T>
        get() = FakeMutableCollection.fromCollection(mutableListOf<T>().apply {
            walk { _, v ->
                this += v
                true
            }
        })
}