package routes

import auth.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import models.Chats
import models.Messages
import models.Users
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

@Serializable
data class ChatResponse(
    @Contextual val id: UUID,
    @Contextual val userId: UUID,
    @Contextual val adminId: UUID
)

@Serializable
data class MessageResponse(
    @Contextual val id: UUID,
    @Contextual val senderId: UUID,
    val content: String,
    val timestamp: String
)

@Serializable
data class SendMessageRequest(
    val content: String
)

fun Route.chatRoutes() {
    authenticate("auth-bearer") {
        route("/chats") {
            // Список чатов
            get {
                val principal = call.principal<UserPrincipal>()!!
                val chats = transaction {
                    if (principal.isAdmin) {
                        Chats.selectAll()
                    } else {
                        Chats.select { Chats.userId eq principal.id }
                    }
                }.map {
                    ChatResponse(
                        id = it[Chats.id],
                        userId = it[Chats.userId],
                        adminId = it[Chats.adminId]
                    )
                }

                call.respond(chats)
            }

            // Создание чата
            post {
                val principal = call.principal<UserPrincipal>()!!

                if (principal.isAdmin) {
                    return@post call.respondText(
                        "Admin can't create chat",
                        status = io.ktor.http.HttpStatusCode.Forbidden
                    )
                }

                // Ищем уже существующий чат
                val existing = transaction {
                    Chats.select { Chats.userId eq principal.id }.singleOrNull()
                }

                if (existing != null) {
                    return@post call.respondText("Chat already exists", status = io.ktor.http.HttpStatusCode.Conflict)
                }

                // Назначаем первого попавшегося админа
                val admin = transaction {
                    Users.select { Users.isAdmin eq true }.limit(1).singleOrNull()
                }

                if (admin == null) {
                    return@post call.respondText(
                        "No admin available",
                        status = io.ktor.http.HttpStatusCode.ServiceUnavailable
                    )
                }

                val chatId = UUID.randomUUID()

                transaction {
                    Chats.insert {
                        it[id] = chatId
                        it[userId] = principal.id
                        it[adminId] = admin[Users.id]
                    }
                }

                call.respond(ChatResponse(chatId, principal.id, admin[Users.id]))
            }

            // Список сообщений в чате
            get("{id}/messages") {
                val principal = call.principal<UserPrincipal>()!!
                val chatId = call.parameters["id"]?.let(UUID::fromString)
                    ?: return@get call.respondText("Invalid chat id", status = io.ktor.http.HttpStatusCode.BadRequest)

                val chat = transaction {
                    Chats.select { Chats.id eq chatId }.singleOrNull()
                } ?: return@get call.respondText("Chat not found", status = io.ktor.http.HttpStatusCode.NotFound)

                if (chat[Chats.userId] != principal.id && chat[Chats.adminId] != principal.id) {
                    return@get call.respondText("Access denied", status = io.ktor.http.HttpStatusCode.Forbidden)
                }

                val messages = transaction {
                    Messages.select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp to SortOrder.ASC)
                        .map {
                            MessageResponse(
                                id = it[Messages.id],
                                senderId = it[Messages.senderId],
                                content = it[Messages.content],
                                timestamp = it[Messages.timestamp].toString()
                            )
                        }
                }

                call.respond(messages)
            }

            // Отправка сообщения
            post("{id}/messages") {
                val principal = call.principal<UserPrincipal>()!!
                val chatId = call.parameters["id"]?.let(UUID::fromString)
                    ?: return@post call.respondText("Invalid chat id", status = io.ktor.http.HttpStatusCode.BadRequest)

                val body = call.receive<SendMessageRequest>()

                val chat = transaction {
                    Chats.select { Chats.id eq chatId }.singleOrNull()
                } ?: return@post call.respondText("Chat not found", status = io.ktor.http.HttpStatusCode.NotFound)

                if (chat[Chats.userId] != principal.id && chat[Chats.adminId] != principal.id) {
                    return@post call.respondText("Access denied", status = io.ktor.http.HttpStatusCode.Forbidden)
                }

                transaction {
                    Messages.insert {
                        it[id] = UUID.randomUUID()
                        it[Messages.chatId] = chatId
                        it[senderId] = principal.id
                        it[content] = body.content
                        it[timestamp] = Instant.now()
                    }
                }

                call.respondText("Message sent", status = io.ktor.http.HttpStatusCode.Created)
            }
        }
    }
}
