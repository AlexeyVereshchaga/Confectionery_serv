package models

import org.jetbrains.exposed.sql.Table

object Products : Table("products") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description")
    val price = decimal("price", precision = 10, scale = 2)
    val image = blob("image")

    override val primaryKey = PrimaryKey(id)
}