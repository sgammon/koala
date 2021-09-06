package mfwgenerics.kotq.expr

class OperationExpr<T : Any>(
    val type: OperationType,
    val args: Collection<QuasiExpr>
): Expr<T> {
}