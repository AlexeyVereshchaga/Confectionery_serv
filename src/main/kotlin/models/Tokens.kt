package models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp


object Tokens : Table("tokens") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(Users.id)
    val accessToken = varchar("access_token", 512)
    val refreshToken = varchar("refresh_token", 512)
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(id)
}