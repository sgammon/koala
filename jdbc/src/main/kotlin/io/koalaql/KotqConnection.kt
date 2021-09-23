package io.koalaql

import io.koalaql.query.PerformableQuery
import io.koalaql.query.PerformableStatement
import io.koalaql.values.RowSequence

interface KotqConnection: AutoCloseable {
    fun query(query: PerformableQuery): RowSequence
    fun statement(statement: PerformableStatement): Int

    fun commit()
    fun rollback()

    /* should guarantee changes are not committed */
    override fun close()
}