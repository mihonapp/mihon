package exh.util

import android.util.SparseArray
import java.util.AbstractMap

class SparseArrayKeyCollection(val sparseArray: SparseArray<out Any?>, var reverse: Boolean = false): AbstractCollection<Int>() {
    override val size get() = sparseArray.size()

    override fun iterator() = object : Iterator<Int> {
        private var index: Int = 0

        /**
         * Returns `true` if the iteration has more elements.
         */
        override fun hasNext() = index < sparseArray.size()

        /**
         * Returns the next element in the iteration.
         */
        override fun next(): Int {
            var idx = index++
            if(reverse) idx = sparseArray.size() - 1 - idx
            return sparseArray.keyAt(idx)
        }
    }
}

class SparseArrayValueCollection<E>(val sparseArray: SparseArray<E>, var reverse: Boolean = false): AbstractCollection<E>() {
    override val size get() = sparseArray.size()

    override fun iterator() = object : Iterator<E> {
        private var index: Int = 0

        /**
         * Returns `true` if the iteration has more elements.
         */
        override fun hasNext() = index < sparseArray.size()

        /**
         * Returns the next element in the iteration.
         */
        override fun next(): E {
            var idx = index++
            if(reverse) idx = sparseArray.size() - 1 - idx
            return sparseArray.valueAt(idx)
        }
    }
}

class SparseArrayCollection<E>(val sparseArray: SparseArray<E>, var reverse: Boolean = false): AbstractCollection<Map.Entry<Int, E>>() {
    override val size get() = sparseArray.size()

    override fun iterator() = object : Iterator<Map.Entry<Int, E>> {
        private var index: Int = 0

        /**
         * Returns `true` if the iteration has more elements.
         */
        override fun hasNext() = index < sparseArray.size()

        /**
         * Returns the next element in the iteration.
         */
        override fun next(): Map.Entry<Int, E> {
            var idx = index++
            if(reverse) idx = sparseArray.size() - 1 - idx
            return AbstractMap.SimpleImmutableEntry(
                    sparseArray.keyAt(idx),
                    sparseArray.valueAt(idx)
            )
        }
    }
}
