package io.koalaql.query

import io.koalaql.expr.AsReference
import io.koalaql.expr.ExprQueryable
import io.koalaql.values.*
import io.koalaql.values.unsafeCastToTwoColumns

interface QueryableResults: Queryable<ResultRow> {
    fun <A : Any> expecting(
        first: AsReference<A>
    ): ExprQueryable<A> =
        ExpectingExprQueryable(this, listOf(first.asReference())) {
            it.unsafeCastToOneColumn()
        }

    fun <A : Any, B : Any> expecting(
        first: AsReference<A>,
        second: AsReference<B>
    ): Queryable<RowWithTwoColumns<A, B>> =
        ExpectingQueryable(this, listOf(first.asReference(), second.asReference())) {
            it.unsafeCastToTwoColumns()
        }

    fun <A : Any, B : Any, C : Any> expecting(
        first: AsReference<A>,
        second: AsReference<B>,
        third: AsReference<C>
    ): Queryable<RowWithThreeColumns<A, B, C>> =
        ExpectingQueryable(this, listOf(first.asReference(), second.asReference(), third.asReference())) {
            it.unsafeCastToThreeColumns()
        }
}