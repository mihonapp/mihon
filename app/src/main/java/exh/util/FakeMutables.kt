package exh.util

// Zero-allocation-overhead mutable collection shims

private inline class CollectionShim<E>(private val coll: Collection<E>) : FakeMutableCollection<E> {
    override val size: Int get() = coll.size

    override fun contains(element: E) = coll.contains(element)

    override fun containsAll(elements: Collection<E>) = coll.containsAll(elements)

    override fun isEmpty() = coll.isEmpty()

    override fun fakeIterator() = coll.iterator()
}

interface FakeMutableCollection<E> : MutableCollection<E>, FakeMutableIterable<E> {
    override fun add(element: E): Boolean {
        throw UnsupportedOperationException("This collection is immutable!")
    }

    override fun addAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException("This collection is immutable!")
    }

    override fun clear() {
        throw UnsupportedOperationException("This collection is immutable!")
    }

    override fun remove(element: E): Boolean {
        throw UnsupportedOperationException("This collection is immutable!")
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException("This collection is immutable!")
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException("This collection is immutable!")
    }

    override fun iterator(): MutableIterator<E> = super.iterator()

    companion object {
        fun <E> fromCollection(coll: Collection<E>): FakeMutableCollection<E> = CollectionShim(coll)
    }
}

private inline class SetShim<E>(private val set: Set<E>) : FakeMutableSet<E> {
    override val size: Int get() = set.size

    override fun contains(element: E) = set.contains(element)

    override fun containsAll(elements: Collection<E>) = set.containsAll(elements)

    override fun isEmpty() = set.isEmpty()

    override fun fakeIterator() = set.iterator()
}

interface FakeMutableSet<E> : MutableSet<E>, FakeMutableCollection<E> {
    /**
     * Adds the specified element to the set.
     *
     * @return `true` if the element has been added, `false` if the element is already contained in the set.
     */
    override fun add(element: E): Boolean = super.add(element)

    override fun addAll(elements: Collection<E>): Boolean = super.addAll(elements)

    override fun clear() = super.clear()

    override fun remove(element: E): Boolean = super.remove(element)

    override fun removeAll(elements: Collection<E>): Boolean = super.removeAll(elements)

    override fun retainAll(elements: Collection<E>): Boolean = super.retainAll(elements)

    override fun iterator(): MutableIterator<E> = super.iterator()

    companion object {
        fun <E> fromSet(set: Set<E>): FakeMutableSet<E> = SetShim(set)
    }
}

private inline class IterableShim<E>(private val iterable: Iterable<E>) : FakeMutableIterable<E> {
    override fun fakeIterator() = iterable.iterator()
}

interface FakeMutableIterable<E> : MutableIterable<E> {
    /**
     * Returns an iterator over the elements of this sequence that supports removing elements during iteration.
     */
    override fun iterator(): MutableIterator<E> = FakeMutableIterator.fromIterator(fakeIterator())

    fun fakeIterator(): Iterator<E>

    companion object {
        fun <E> fromIterable(iterable: Iterable<E>): FakeMutableIterable<E> = IterableShim(iterable)
    }
}

private inline class IteratorShim<E>(private val iterator: Iterator<E>) : FakeMutableIterator<E> {
    /**
     * Returns `true` if the iteration has more elements.
     */
    override fun hasNext() = iterator.hasNext()

    /**
     * Returns the next element in the iteration.
     */
    override fun next() = iterator.next()
}


interface FakeMutableIterator<E> : MutableIterator<E> {
    /**
     * Removes from the underlying collection the last element returned by this iterator.
     */
    override fun remove() {
        throw UnsupportedOperationException("This set is immutable!")
    }

    companion object {
        fun <E> fromIterator(iterator: Iterator<E>) : FakeMutableIterator<E> = IteratorShim(iterator)
    }
}

private inline class EntryShim<K, V>(private val entry: Map.Entry<K, V>) : FakeMutableEntry<K, V> {
    /**
     * Returns the key of this key/value pair.
     */
    override val key: K
        get() = entry.key
    /**
     * Returns the value of this key/value pair.
     */
    override val value: V
        get() = entry.value
}

private inline class PairShim<K, V>(private val pair: Pair<K, V>) : FakeMutableEntry<K, V> {
    /**
     * Returns the key of this key/value pair.
     */
    override val key: K get() = pair.first
    /**
     * Returns the value of this key/value pair.
     */
    override val value: V get() = pair.second
}

interface FakeMutableEntry<K, V> : MutableMap.MutableEntry<K, V> {
    override fun setValue(newValue: V): V {
        throw UnsupportedOperationException("This entry is immutable!")
    }

    companion object {
        fun <K, V> fromEntry(entry: Map.Entry<K, V>): FakeMutableEntry<K, V> = EntryShim(entry)

        fun <K, V> fromPair(pair: Pair<K, V>): FakeMutableEntry<K, V> = PairShim(pair)

        fun <K, V> fromPair(key: K, value: V) = object : FakeMutableEntry<K, V> {
            /**
             * Returns the key of this key/value pair.
             */
            override val key: K = key
            /**
             * Returns the value of this key/value pair.
             */
            override val value: V = value
        }
    }
}