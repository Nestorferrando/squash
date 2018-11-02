package org.jetbrains.squash.dialects.sqlite

import org.jetbrains.squash.drivers.*
import org.jetbrains.squash.schema.*

class SqLiteTransaction(connection: JDBCConnection) : JDBCTransaction(connection) {
    override suspend fun databaseSchema(): DatabaseSchema = SqLiteDatabaseSchema(this)
}