package models

import org.jetbrains.exposed.sql.Table

object Chats : Table("chats") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(Users.id)
    val adminId = uuid("admin_id").references(Users.id)

    override val primaryKey = PrimaryKey(id)
}