package io.koalaql.jdbc

import io.koalaql.*
import io.koalaql.data.JdbcTypeMappings
import io.koalaql.ddl.Table
import io.koalaql.ddl.diff.SchemaChange
import io.koalaql.dialect.SqlDialect
import io.koalaql.event.ConnectionEventWriter
import io.koalaql.event.DataSourceEvent
import io.koalaql.query.built.BuiltDml
import io.koalaql.sql.SqlText
import java.sql.Connection

class JdbcDataSource(
    val schema: JdbcSchemaDetection,
    val dialect: SqlDialect,
    val provider: JdbcProvider,
    val typeMappings: JdbcTypeMappings,
    val declareBy: DeclareStrategy,
    val events: DataSourceEvent
): SchemaDataSource {
    override fun detectChanges(tables: List<Table>): SchemaChange = provider.connect().use { jdbc ->
        jdbc.autoCommit = true

        val dbName = checkNotNull(jdbc.catalog?.takeIf { it.isNotBlank() })
            { "no database selected" }

        schema.detectChanges(dbName, jdbc.metaData, tables)
    }

    private fun applyDdl(ddl: List<SqlText>) {
        provider.connect().use { jdbc ->
            jdbc.autoCommit = true

            val connection = JdbcConnection(
                jdbc,
                dialect,
                typeMappings,
                ConnectionEventWriter.Discard
            )

            ddl.forEach {
                connection.prepareAndThen(it) {
                    use {
                        execute()
                    }
                }
            }
        }
    }

    override fun changeSchema(changes: SchemaChange) {
        val ddl = dialect.ddl(changes)

        applyDdl(ddl)
    }

    override fun declareTables(tables: List<Table>) {
        tables.forEach { table ->
            table.columns.forEach {
                typeMappings.register(it.builtDef.columnType)
            }
        }

        when (declareBy) {
            DeclareOnly -> return
            CreateIfNotExists -> {
                val diff = SchemaChange()

                tables.forEach {
                    diff.tables.created[it.tableName] = it
                }

                changeSchema(diff)
            }
            is ReconcileTables -> {
                val filtered = declareBy.filterChanges(
                    detectChanges(tables)
                )

                val ddl = ReconciledDdl(
                    applied = dialect.ddl(filtered.applied),
                    unexpected = dialect.ddl(filtered.unexpected),
                    ignored = dialect.ddl(filtered.ignored)
                )

                val appliedEvent = events.changes(filtered, ddl)

                check (ddl.unexpected.isEmpty()) {
                    val ddlLines = ddl.unexpected
                        .asSequence()
                        .map { "$it".trim() }
                        .joinToString("\n")

                    "Unexpected DDL:\n$ddlLines"
                }

                applyDdl(ddl.applied)
                appliedEvent.applied(ddl.applied)
            }
        }
    }

    override fun connect(isolation: Isolation, events: ConnectionEventWriter): JdbcConnection {
        val jdbc = provider.connect()

        if (jdbc.autoCommit) jdbc.autoCommit = false

        val desiredIso = when (isolation) {
            Isolation.READ_UNCOMMITTED -> Connection.TRANSACTION_READ_COMMITTED
            Isolation.READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED
            Isolation.REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ
            Isolation.SERIALIZABLE -> Connection.TRANSACTION_SERIALIZABLE
        }

        if (jdbc.transactionIsolation != desiredIso) {
            jdbc.transactionIsolation = desiredIso
        }

        return JdbcConnection(
            jdbc,
            dialect,
            typeMappings,
            this.events.connect() + events
        )
    }

    override fun generateSql(dml: BuiltDml): SqlText? = dialect.compile(dml)

    fun close() {
        provider.close()
    }
}