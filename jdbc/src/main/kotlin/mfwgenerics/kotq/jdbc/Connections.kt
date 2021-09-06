package mfwgenerics.kotq.jdbc

import mfwgenerics.kotq.query.Performable

fun <T> Performable<T>.performWith(cxn: JdbcConnection): T =
    cxn.perform(this)
