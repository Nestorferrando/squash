package org.jetbrains.squash.dialect

import org.jetbrains.squash.*
import org.jetbrains.squash.definition.*

open class BaseDefinitionSQLDialect(val dialect: SQLDialect) : DefinitionSQLDialect {

    override fun tableSQL(table: Table): List<SQLStatement> {
        val tableSQL = SQLBuilder().apply {
            append("CREATE TABLE IF NOT EXISTS ${dialect.idSQL(table.tableName)}")
            if (table.tableColumns.any()) {
                append(" (")
                table.tableColumns.forEachIndexed { index, column ->
                    if (index > 0)
                        append(", ")
                    columnDefinitionSQL(this, column)
                }

                appendPrimaryKeys(table)
                append(")")
            }
        }.build()
        val indices = indicesSQL(table)
        return listOf(tableSQL) + indices
    }

    private fun indicesSQL(table: Table): List<SQLStatement> =
            table.constraints.filterIsInstance<IndexConstraint>().map {
                SQLBuilder().apply {
                    val unique = if (it.unique) " UNIQUE" else ""
                    append("CREATE$unique INDEX ${dialect.idSQL(it.name)} ON ${dialect.idSQL(table.tableName)} (")
                    it.columns.forEachIndexed { index, column ->
                        if (index > 0)
                            append(", ")
                        append(dialect.idSQL(column.name))
                    }
                    append(")")
                }.build()
            }

    private fun SQLBuilder.appendPrimaryKeys(table: Table) {
        val primaryKeys = table.constraints.filterIsInstance<PrimaryKeyConstraint>()
        when (primaryKeys.size) {
            1 -> {
                append(", ")
                primaryKeyDefinitionSQL(primaryKeys[0], table)
            }
            0 -> {
                val autoIncrement = table.tableColumns.filterIsInstance<AutoIncrementColumn<*>>()
                if (autoIncrement.any()) {
                    append(", ")
                    val name = Identifier("PK_${dialect.nameSQL(table.tableName)}")
                    val pkAutoIncrement = PrimaryKeyConstraint(name, table, autoIncrement)
                    primaryKeyDefinitionSQL(pkAutoIncrement, table)
                }
            }
            else -> error("Table cannot have more than one PrimaryKey constraint")
        }
    }

    protected open fun SQLBuilder.primaryKeyDefinitionSQL(primaryKey: PrimaryKeyConstraint, table: Table) {
        append("CONSTRAINT ${dialect.idSQL(primaryKey.name)} PRIMARY KEY (")
        append(primaryKey.columns.map { dialect.idSQL(it.name) }.joinToString())
        append(")")
    }

    protected open fun columnDefinitionSQL(builder: SQLBuilder, column: Column<*>): Unit = with(builder) {
        append(dialect.idSQL(column.name))
        append(" ")
        columnTypeSQL(this, column, emptySet())
    }

    protected open fun columnTypeSQL(builder: SQLBuilder, column: Column<*>, properties: Set<BaseSQLDialect.ColumnProperty>): Unit = with(builder) {
        when (column) {
            is DataColumn -> {
                if (BaseSQLDialect.ColumnProperty.NULLABLE in properties) {
                    columnTypeSQL(this, column.type)
                    append(" NULL")
                } else {
                    columnTypeSQL(this, column.type)
                    append(" NOT NULL")
                }
            }

            is NullableColumn -> {
                require(BaseSQLDialect.ColumnProperty.AUTOINCREMENT !in properties) { "Column ${column.name} cannot be both AUTOINCREMENT and NULL" }
                columnTypeSQL(this, column.column, properties + BaseSQLDialect.ColumnProperty.NULLABLE)
            }

            is AutoIncrementColumn -> {
                require(BaseSQLDialect.ColumnProperty.NULLABLE !in properties) { "Column ${column.name} cannot be both AUTOINCREMENT and NULL" }
                columnTypeSQL(this, column.column, properties + BaseSQLDialect.ColumnProperty.AUTOINCREMENT).toString()
                append(" AUTO_INCREMENT")
            }

            is DefaultValueColumn<*> -> {
                columnTypeSQL(this, column.column, properties + BaseSQLDialect.ColumnProperty.DEFAULT).toString()
                append(" DEFAULT ")
                append(dialect.literalSQL(column.value))
            }

            else -> error("Column class '${column.javaClass.simpleName}' is not supported by $this")
        }
    }

    protected open fun columnTypeSQL(builder: SQLBuilder, type: ColumnType): Unit = with(builder) {
        when (type) {
            is ReferenceColumnType<*> -> columnTypeSQL(this, type.column.type)
            is CharColumnType -> append("CHAR")
            is LongColumnType -> append("BIGINT")
            is IntColumnType -> append("INT")
            is DecimalColumnType -> append("DECIMAL(${type.scale}, ${type.precision})")
            is EnumColumnType<*> -> append("INT")
            is DateColumnType -> append("DATE")
            is DateTimeColumnType -> append("DATETIME")
            is BinaryColumnType -> append("VARBINARY(${type.length})")
            is UUIDColumnType -> append("BINARY(16)")
            is StringColumnType -> {
                val sqlType = when (type.length) {
                    in 1..255 -> "VARCHAR(${type.length})"
                    else -> "TEXT"
                }
                if (type.collate == null)
                    append(sqlType)
                else
                    append(sqlType + " COLLATE ${type.collate}")
            }
            else -> error("Column type '$type' is not supported by $this")
        }
    }
}