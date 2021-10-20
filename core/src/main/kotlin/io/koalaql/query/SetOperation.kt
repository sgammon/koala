package io.koalaql.query

import io.koalaql.query.built.BuiltQueryBody
import io.koalaql.query.built.BuiltSetOperation
import io.koalaql.query.built.QueryBodyBuilder
import io.koalaql.query.fluent.UnionOperand
import io.koalaql.query.fluent.Unionable

class SetOperation(
    val of: Unionable,
    val against: UnionOperand,
    val type: SetOperationType,
    val distinctness: Distinctness
): Unionable {
    override fun BuiltQueryBody.buildIntoQueryBody(): QueryBodyBuilder? {
        val op = BuiltSetOperation(
            type = type,
            distinctness = distinctness
        )

        with (against) { op.buildIntoSetOperation() }

        setOperations.add(op)

        return of
    }
}