package models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Messages : Table("messages") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val chatId = uuid("chat_id").references(Chats.id)
    val senderId = uuid("sender_id").references(Users.id)
    val content = text("content")
    val timestamp = timestamp("timestamp")

    override val primaryKey = PrimaryKey(id)
}