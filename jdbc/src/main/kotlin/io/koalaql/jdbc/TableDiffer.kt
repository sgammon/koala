package io.koalaql.jdbc

import io.koalaql.data.*
import io.koalaql.ddl.BaseColumnType
import io.koalaql.ddl.IndexType
import io.koalaql.ddl.Table
import io.koalaql.ddl.TableColumn
import io.koalaql.ddl.diff.ChangedDefault
import io.koalaql.ddl.diff.ColumnDiff
import io.koalaql.ddl.diff.SchemaDiff
import io.koalaql.ddl.diff.TableDiff
import java.sql.DatabaseMetaData
import java.sql.Types

class TableDiffer(
    val dbName: String,
    val metadata: DatabaseMetaData
) {
    private data class ColumnTypeInfo(
        val tag: Int,
        val name: String,
        val columnSize: Int,
        val decimalDigits: Int
    )

    private fun diffColumnType(dataType: UnmappedDataType<*>, info: ColumnTypeInfo): Boolean {
        return when (dataType) {
            BOOLEAN -> info.tag != Types.BOOLEAN
            DOUBLE -> info.tag != Types.DOUBLE
            FLOAT -> info.tag != Types.FLOAT
            INTEGER -> info.tag != Types.INTEGER
            SMALLINT -> info.tag != Types.SMALLINT
            TEXT -> info.tag != Types.CLOB
            TINYINT -> info.tag != Types.TINYINT
            is VARCHAR -> info.tag != Types.VARCHAR
                || info.columnSize != dataType.maxLength
            else -> false
        }
    }

    private fun diffColumns(table: Table, result: TableDiff) {
        val tableName = table.relvarName

        val expectedColumnsByName = hashMapOf<String, TableColumn<*>>()

        table.columns.associateByTo(expectedColumnsByName) { it.symbol }

        val columns = metadata.getColumns(dbName, null, tableName, null)

        while (columns.next()) {
            val name = columns.getString("COLUMN_NAME")

            val typeInfo = ColumnTypeInfo(
                tag = columns.getInt("DATA_TYPE"),
                name = columns.getString("TYPE_NAME"),
                columnSize = columns.getInt("COLUMN_SIZE"),
                decimalDigits = columns.getInt("DECIMAL_DIGITS")
            )

            val expected = expectedColumnsByName.remove(name)

            if (expected == null) {
                result.columns.dropped.add(name)
                continue
            }

            val def = expected.builtDef

            val isAutoincrement = when (columns.getString("IS_AUTOINCREMENT")) {
                "YES" -> true
                else -> false
            }

            val diff = ColumnDiff(
                newColumn = expected,
                type = if (diffColumnType(def.columnType.dataType, typeInfo)) {
                    BaseColumnType(def.columnType.dataType)
                } else null,
                notNull = when (columns.getString("IS_NULLABLE")) {
                    "YES" -> if (def.notNull) def.notNull else null
                    "NO" -> if (!def.notNull) def.notNull else null
                    else -> null
                },
                changedDefault = when (columns.getString("COLUMN_DEF")) {
                    null -> if (def.default != null) {
                        ChangedDefault(def.default)
                    } else null
                    else -> if (def.default == null && !isAutoincrement) {
                        ChangedDefault(def.default)
                    } else null
                },
                isAutoIncrement = null
            )

            if (!diff.doesNothing()) {
                result.columns.altered[name] = diff
            }
        }

        expectedColumnsByName.forEach { (name, def) ->
            result.columns.created[name] = def
        }
    }

    private data class IndexPart(
        val unique: Boolean,
        val ordinal: Int,
        val columnName: String
    )

    private data class PrimaryKeyPart(
        val name: String,
        val ordinal: Int,
        val columnName: String
    )

    private data class IndexWithKeyNames(
        val name: String,
        val type: IndexType,
        val columns: List<String>
    ) {
        fun equivalent(other: IndexWithKeyNames): Boolean {
            if (type != other.type) return false
            if (columns.size != other.columns.size) return false

            columns.forEachIndexed { ix, col ->
                if (col != other.columns[ix]) return false
            }

            return true
        }
    }

    private fun fetchExistingPrimaryKey(tableName: String): IndexWithKeyNames? {
        val pkResults = metadata.getPrimaryKeys(dbName, null, tableName)

        val pkParts = arrayListOf<PrimaryKeyPart>()

        while (pkResults.next()) {
            pkParts.add(PrimaryKeyPart(
                name = pkResults.getString("PK_NAME"),
                ordinal = pkResults.getInt("KEY_SEQ"),
                columnName = pkResults.getString("COLUMN_NAME")
            ))
        }

        return pkParts
            .takeIf { it.isNotEmpty() }
            ?.let { parts ->
                parts.sortBy { it.ordinal }

                IndexWithKeyNames(
                    name = parts.first().name,
                    type = IndexType.PRIMARY,
                    columns = parts.map { it.columnName }
                )
            }
    }

    private fun diffKeys(table: Table, result: TableDiff) {
        val tableName = table.relvarName

        val existingPrimaryKey = fetchExistingPrimaryKey(tableName)

        val indexResults = metadata.getIndexInfo(dbName, null, tableName, false, false)

        val indexInfosByName = hashMapOf<String, ArrayList<IndexPart>>()

        while (indexResults.next()) {
            val name = indexResults.getString("INDEX_NAME")

            if (name == null || name == existingPrimaryKey?.name) continue

            val infos = indexInfosByName.getOrPut(name) { arrayListOf() }

            infos.add(IndexPart(
                unique = !indexResults.getBoolean("NON_UNIQUE"),
                ordinal = indexResults.getInt("ORDINAL_POSITION"),
                columnName = indexResults.getString("COLUMN_NAME")
            ))
        }

        val expectedIndexesByName = table.indexes.associateByTo(hashMapOf(), { it.name })

        indexInfosByName.forEach { (name, parts) ->
            parts.sortBy { it.ordinal }

            val existing = IndexWithKeyNames(
                name = name,
                type = if (parts.first().unique) {
                    IndexType.UNIQUE
                } else {
                    IndexType.INDEX
                },
                columns = parts.map { it.columnName }
            )

            val expected = expectedIndexesByName.remove(name)

            if (expected == null) {
                result.indexes.dropped.add(name)
            } else {
                val converted = IndexWithKeyNames(
                    name = expected.name,
                    type = expected.def.type,
                    columns = expected.def.keys.keys.map { expr ->
                        when (expr) {
                            is TableColumn -> expr.symbol
                            else -> error("not implemented")
                        }
                    }
                )

                if (!existing.equivalent(converted)) {
                    result.indexes.altered[name] = expected.def
                }
            }
        }

        expectedIndexesByName.forEach { (name, it) ->
            result.indexes.created[name] = it.def
        }
    }

    fun diffTable(
        table: Table
    ): TableDiff {
        val result = TableDiff(table)

        diffColumns(table, result)
        diffKeys(table, result)

        return result
    }

    fun declareTables(
        tables: List<Table>
    ): SchemaDiff {
        val diff = SchemaDiff()

        val toCreate = tables.associateByTo(hashMapOf()) { it.relvarName }
        val toDiff = arrayListOf<Table>()

        val rs = metadata.getTables(
            dbName,
            null,
            null,
            arrayOf("TABLE")
        )

        while (rs.next()) {
            val name = rs.getString("TABLE_NAME")

            toCreate.remove(name)?.let {
                toDiff.add(it)
            }
        }

        toCreate.forEach { (name, table) -> diff.tables.created[name] = table }

        toDiff.forEach { table ->
            diff.tables.altered[table.relvarName] = diffTable(table)
        }

        return diff
    }
}