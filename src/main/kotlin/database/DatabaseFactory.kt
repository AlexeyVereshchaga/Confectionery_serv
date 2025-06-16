package database

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import com.typesafe.config.ConfigFactory

object DatabaseFactory {
    fun init() {
        val config = ConfigFactory.load().getConfig("database")
        val url = config.getString("url")
        val driver = config.getString("driver")
        val user = config.getString("user")
        val password = config.getString("password")

        Database.connect(url, driver = driver, user = user, password = password)

        transaction {
            SchemaUtils.create(Users, Products, Chats, Messages, Tokens)
        }
    }
}
