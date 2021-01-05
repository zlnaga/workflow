package com.axacat.workflow.util

internal class DistinctMutableList<E>(
    val list: MutableList<E>
) : MutableList<E> by list {
    override fun add(element: E): Boolean {
        return if (!contains(element)) {
            list.add(element)
        } else {
            false
        }
    }

    override fun add(index: Int, element: E) {
        if (!contains(element)) {
            list.add(index, element)
        }
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        val newElements = elements.filter { !contains(it) }
        return list.addAll(index, newElements)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        val newElements = elements.filter { !contains(it) }
        return list.addAll(newElements)
    }
}

internal fun <E> distinctMutableListOf(vararg elements: E) = DistinctMutableList(mutableListOf(*elements))