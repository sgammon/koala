package io.koalaql.query.fluent

import io.koalaql.dsl.values
import io.koalaql.query.Subqueryable
import io.koalaql.query.built.BuildsIntoInsert
import io.koalaql.query.built.BuiltInsert
import io.koalaql.query.built.BuiltSubquery
import io.koalaql.values.ValuesRow

interface Withed: BuildsIntoInsert, Joinable {
    private class Insert(
        val ignore: Boolean,
        val of: Withed,
        val query: BuiltSubquery
    ): OnConflictable {
        override fun BuiltInsert.buildIntoInsert(): BuildsIntoInsert? {
            ignore = this@Insert.ignore
            query = this@Insert.query
            return of
        }
    }

    fun insert(queryable: Subqueryable): OnConflictable =
        Insert(false, this, queryable.buildQuery())

    fun insert(row: ValuesRow): OnConflictable =
        insert(values(row))

    fun insertIgnore(queryable: Subqueryable): OnConflictable =
        Insert(true, this, queryable.buildQuery())

    fun insertIgnore(row: ValuesRow): OnConflictable =
        insertIgnore(values(row))
}