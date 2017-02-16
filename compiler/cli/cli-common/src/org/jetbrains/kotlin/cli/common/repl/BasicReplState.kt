/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cli.common.repl

import java.io.Serializable
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

data class LineId(override val no: Int, private val codeHash: Int) : ILineId, Serializable {

    constructor(no: Int, code: String): this(no, code.hashCode())
    constructor(codeLine: ReplCodeLine): this(codeLine.no, codeLine.code.hashCode())

    override fun compareTo(other: ILineId): Int = (other as? LineId)?.let {
        val c = no.compareTo(it.no)
        if (c == 0) codeHash.compareTo(it.codeHash)
        else c
    } ?: -1 // TODO: check if it doesn't break something

    companion object {
        private val serialVersionUID: Long = 8328353000L
    }
}

open class BasicReplStageHistory<T>(override val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()) : IReplStageHistory<T>, ArrayList<ReplHistoryRecord<T>>() {

    override fun push(id: ILineId, item: T) {
        lock.write {
            add(ReplHistoryRecord(id, item))
        }
    }

    override fun pop(): ReplHistoryRecord<T>? = lock.write { if (isEmpty()) null else removeAt(lastIndex) }

    override fun resetTo(id: ILineId): Iterable<ILineId> {
        lock.write {
            val idx = indexOfFirst { it.id == id }
            if (idx < 0) throw java.util.NoSuchElementException("Cannot rest to inexistent line ${id.no}")
            if (idx < lastIndex) {
                val removed = asSequence().drop(idx + 1).map { it.id }.toList()
                removeRange(idx + 1, lastIndex)
                return removed
            }
            else return emptyList()
        }
    }
}

class BasicReplStageState<HistoryItemT>: IReplStageState<HistoryItemT> {

    override val lock = ReentrantReadWriteLock()

    override val history: BasicReplStageHistory<HistoryItemT> = BasicReplStageHistory(lock)
}
