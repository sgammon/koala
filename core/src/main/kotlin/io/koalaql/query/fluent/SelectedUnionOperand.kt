package io.koalaql.query.fluent

import io.koalaql.query.Subqueryable
import io.koalaql.query.built.BuiltSetOperation
import io.koalaql.query.built.BuiltUnionOperand

interface SelectedUnionOperand: Subqueryable, UnionOperand {
    override fun BuiltSetOperation.buildIntoSetOperation()
}