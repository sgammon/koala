package io.koalaql.query.fluent

import io.koalaql.Assignment
import io.koalaql.dsl.as_
import io.koalaql.dsl.label
import io.koalaql.expr.*
import io.koalaql.query.*
import io.koalaql.query.built.*
import io.koalaql.values.*

interface Selectable: BuildsIntoQueryBody, QueryableUnionOperand<ResultRow> {
    override fun BuiltQuery.buildInto(): QueryBuilder? =
        with (selectAll()) { buildInto() }

    override fun BuiltQuery.buildIntoQueryTail(type: SetOperationType, distinctness: Distinctness) {
        with (selectAll()) { buildIntoQueryTail(type, distinctness) }
    }

    override fun with(type: WithType, queries: List<BuiltWith>): Queryable<ResultRow> =
        selectAll().with(type, queries)

    private class Select(
        val of: Selectable,
        val references: List<SelectArgument>,
        val includeAll: Boolean
    ): QueryableUnionOperand<ResultRow> {
        fun buildQuery(): BuiltUnionOperandQuery = BuiltSelectQuery(
            BuiltQueryBody.from(of),
            references,
            includeAll
        )

        override fun BuiltQuery.buildInto(): QueryBuilder? {
            head = buildQuery()
            return null
        }

        override fun BuiltQuery.buildIntoQueryTail(type: SetOperationType, distinctness: Distinctness) {
            unioned.add(BuiltSetOperation(
                type = type,
                distinctness = distinctness,
                body = buildQuery()
            ))
        }

        override fun perform(ds: BlockingPerformer): RowSequence<ResultRow> =
            ds.query(BuiltQuery.from(this))

        override fun with(type: WithType, queries: List<BuiltWith>) = object : Queryable<ResultRow> {
            override fun perform(ds: BlockingPerformer): RowSequence<ResultRow> =
                ds.query(BuiltQuery.from(this))

            override fun BuiltQuery.buildInto(): QueryBuilder? {
                withType = type
                withs = queries

                return this@Select
            }
        }
    }

    fun selectAll(vararg references: SelectArgument): QueryableUnionOperand<ResultRow> =
        Select(this, references.asList(), true)

    override fun perform(ds: BlockingPerformer): RowSequence<ResultRow> =
        selectAll().perform(ds)

    fun select(references: List<SelectArgument>): QueryableUnionOperand<ResultRow> = if (references.isEmpty()) {
        val x = label<Int>()

        CastQueryableUnionOperand(select(1 as_ x)) {
            RowSequenceEmptyMask(it)
        }
    } else {
        Select(this, references, false)
    }

    fun select(vararg references: SelectArgument): QueryableUnionOperand<ResultRow> =
        select(references.asList())

    fun <A : Any> select(labeled: SelectOperand<A>): ExprQueryableUnionOperand<A> =
        CastExprQueryableUnionOperand(select(listOf(labeled))) {
            it.unsafeCastToOneColumn()
        }

    fun <A : Any, B : Any> select(
        first: SelectOperand<A>,
        second: SelectOperand<B>
    ): QueryableUnionOperand<RowWithTwoColumns<A, B>> =
        CastQueryableUnionOperand(select(listOf(first, second))) {
            it.unsafeCastToTwoColumns()
        }

    fun <A : Any, B : Any, C : Any> select(
        first: SelectOperand<A>,
        second: SelectOperand<B>,
        third: SelectOperand<C>
    ): QueryableUnionOperand<RowWithThreeColumns<A, B, C>> =
        CastQueryableUnionOperand(select(listOf(first, second, third))) {
            it.unsafeCastToThreeColumns()
        }

    private class Update(
        val of: Selectable,
        val assignments: List<Assignment<*>>
    ): Updated {
        override fun BuiltUpdate.buildInto(): BuildsIntoUpdate? {
            query = BuiltQueryBody.from(of)
            assignments = this@Update.assignments

            return null
        }
    }

    fun update(assignments: List<Assignment<*>>): Updated =
        Update(this, assignments)

    fun update(vararg assignments: Assignment<*>): Updated =
        update(assignments.asList())

    private class Delete(
        val of: Selectable
    ): Deleted {
        override fun BuiltDelete.buildInto(): BuildsIntoDelete? {
            query = BuiltQueryBody.from(of)
            return null
        }
    }

    fun delete(): Deleted = Delete(this)
}

