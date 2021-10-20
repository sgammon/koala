package io.koalaql.query.fluent

import io.koalaql.query.built.BuiltQueryBody
import io.koalaql.query.built.QueryBodyBuilder

interface Limitable: Lockable {
    private class Limit(
        val of: Limitable,
        val rows: Int
    ): Lockable {
        override fun BuiltQueryBody.buildIntoQueryBody(): QueryBodyBuilder? {
            limit = rows

            return of
        }
    }

    fun limit(rows: Int): Lockable = Limit(this, rows)
}