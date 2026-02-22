package com.neoutils.finsight.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow

class ObservableMutableMap<K, V>(
    private val map: MutableMap<K, V> = mutableMapOf()
) : MutableMap<K, V>, Flow<Map<K, V>> {

    private val flow = MutableStateFlow(map.toMap())

    override val keys get() = map.keys

    override val values get() = map.values

    override val entries get() = map.entries

    override val size get() = map.size

    fun <R> update(block: MutableMap<K, V>.() -> R): R {
        return block(map).also {
            flow.value = map.toMap()
        }
    }

    override fun put(key: K, value: V): V? {
        return update {
            put(key, value)
        }
    }

    override fun remove(key: K): V? {
        return update {
            remove(key)
        }
    }

    override fun putAll(from: Map<out K, V>) {
        update {
            putAll(from)
        }
    }

    override fun clear() {
        update {
            clear()
        }
    }

    override fun isEmpty() = map.isEmpty()

    override fun containsKey(key: K) = map.containsKey(key)

    override fun containsValue(value: V) = map.containsValue(value)

    override fun get(key: K) = map[key]

    override suspend fun collect(
        collector: FlowCollector<Map<K, V>>
    ) {
        flow.collect(collector)
    }
}