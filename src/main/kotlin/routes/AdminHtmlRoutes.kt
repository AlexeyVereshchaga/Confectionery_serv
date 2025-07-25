package routes

import AdminSession
import auth.PasswordHasher
import auth.requireAdminSession
import dto.AdminChatListItem
import dto.AdminChatMessageItem
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.*
import models.Chats
import models.Messages
import models.Products
import models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Clock
import java.util.*

fun Route.adminHtmlRoutes() {
    route("/admin") {
        // Login form
        get("/login") {
            call.respondHtml {
                head { title { +"Admin Login" } }
                body {
                    h2 { +"Login as Admin" }
                    form(action = "/admin/login", method = FormMethod.post) {
                        p {
                            label { +"Email: " }
                            textInput(name = "email")
                        }
                        p {
                            label { +"Password: " }
                            passwordInput(name = "password")
                        }
                        p {
                            submitInput { value = "Login" }
                        }
                    }
                }
            }
        }

        // Login processing
        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"].orEmpty()
            val password = params["password"].orEmpty()

            val admin = transaction {
                Users.select {
                    Users.email eq email and (Users.isAdmin eq true)
                }.singleOrNull()
            }

            if (admin != null && PasswordHasher.verify(password, admin[Users.passwordHash])) {
                call.sessions.set(AdminSession(admin[Users.id]))
                call.respondRedirect("/admin/products")
            } else {
                call.respondHtml {
                    body {
                        p { +"Invalid credentials" }
                        a(href = "/admin/login") { +"Try again" }
                    }
                }
            }
        }

        // Logout
        get("/logout") {
            call.sessions.clear<AdminSession>()
            call.respondRedirect("/admin/login")
        }

        get("/products") {
            val adminId = call.requireAdminSession() ?: return@get

            val products = transaction {
                Products.selectAll().map {
                    Triple(
                        it[Products.id],
                        it[Products.name],
                        it[Products.price]
                    )
                }
            }

            call.respondHtml {
                head { title { +"Admin Products" } }
                body {
                    h2 { +"Products" }
                    a(href = "/admin/products/new") { +"Add Product" }
                    table {
                        tr {
                            th { +"Name" }
                            th { +"Price" }
                            th { +"Actions" }
                        }
                        products.forEach { (id, name, price) ->
                            tr {
                                td { +name }
                                td { +"$price" }
                                td {
                                    a(href = "/admin/products/$id/edit") { +"Edit" }
                                }
                            }
                        }
                    }
                    p { a(href = "/admin/logout") { +"Logout" } }
                }
            }
        }
//форма создания товара
        get("/products/new") {
            val adminId = call.requireAdminSession() ?: return@get

            call.respondHtml {
                head { title { +"New Product" } }
                body {
                    h2 { +"Create Product" }
                    form(action = "/admin/products/new", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                        p { textInput(name = "name") { placeholder = "Name" } }
                        p { textArea(rows = "5", cols = "40") { +"Description" } }
                        p { textInput(name = "price") { placeholder = "Price" } }
                        p { fileInput(name = "image") }
                        p { submitInput { value = "Create" } }
                    }
                    p { a(href = "/admin/products") { +"Back to products" } }
                }
            }
        }

        //— создание товара
        post("/products/new") {
            val adminId = call.requireAdminSession() ?: return@post

            val multipart = call.receiveMultipart()

            var name: String? = null
            var description: String? = null
            var price: String? = null
            var image: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "name" -> name = part.value
                        "description" -> description = part.value
                        "price" -> price = part.value
                    }
                    is PartData.FileItem -> if (part.name == "image") {
                        image = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (name == null || description == null || price == null || image == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "All fields required")
            }

            transaction {
                Products.insert {
                    it[id] = UUID.randomUUID()
                    it[Products.name] = name!!
                    it[Products.description] = description!!
                    it[Products.price] = price!!.toBigDecimal()
                    it[Products.image] = ExposedBlob(image!!)
                }
            }

            call.respondRedirect("/admin/products")
        }

        //GET /admin/products/{id}/edit — форма редактирования
        get("/products/{id}/edit") {
            val adminId = call.requireAdminSession() ?: return@get
            val productId = call.parameters["id"]?.let(UUID::fromString) ?: return@get call.respond(HttpStatusCode.BadRequest)

            val product = transaction {
                Products.select { Products.id eq productId }.singleOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respondHtml {
                head { title { +"Edit Product" } }
                body {
                    h2 { +"Edit Product" }
                    form(action = "/admin/products/$productId/edit", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                        p { textInput(name = "name") { value = product[Products.name] } }
                        p {
                            textArea(rows = "5", cols = "40") {
                                +product[Products.description]
                            }
                        }
                        p { textInput(name = "price") { value = product[Products.price].toPlainString() } }
                        p { fileInput(name = "image") }
                        p { submitInput { value = "Update" } }
                    }
                    p { a(href = "/admin/products") { +"Back to products" } }
                }
            }
        }

        //✅ POST /admin/products/{id}/edit — сохранение изменений

        post("/products/{id}/edit") {
            val adminId = call.requireAdminSession() ?: return@post
            val productId = call.parameters["id"]?.let(UUID::fromString) ?: return@post call.respond(HttpStatusCode.BadRequest)

            val multipart = call.receiveMultipart()

            var name: String? = null
            var description: String? = null
            var price: String? = null
            var image: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "name" -> name = part.value
                        "description" -> description = part.value
                        "price" -> price = part.value
                    }
                    is PartData.FileItem -> if (part.name == "image") {
                        image = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            transaction {
                Products.update({ Products.id eq productId }) {
                    if (name != null) it[Products.name] = name!!
                    if (description != null) it[Products.description] = description!!
                    if (price != null) it[Products.price] = price!!.toBigDecimal()
                    if (image != null) it[Products.image] = ExposedBlob(image!!)
                }
            }

            call.respondRedirect("/admin/products")
        }

        get("/chats") {
            val adminId = call.requireAdminSession() ?: return@get

            // Выбираем все чаты, в которых участвует этот админ
            val items = transaction {
                // Получаем чаты + email пользователя
                val rows = (Chats innerJoin Users)
                    .slice(Chats.id, Chats.userId, Users.email)
                    .select { Chats.adminId eq adminId }
                    .toList()

                rows.map { row ->
                    val chatId = row[Chats.id]
                    val userEmail = row[Users.email]

                    // Последнее сообщение в чате
                    val lastMsgRow = Messages
                        .select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp to SortOrder.DESC)
                        .limit(1)
                        .singleOrNull()

                    val lastMessage = lastMsgRow?.get(Messages.content)
                    val lastTs = lastMsgRow?.get(Messages.timestamp)

                    AdminChatListItem(
                        chatId = chatId,
                        userEmail = userEmail,
                        lastMessage = lastMessage,
                        lastTimestamp = lastTs
                    )
                }.sortedByDescending { it.lastTimestamp } // последние активные сверху
            }

            call.respondHtml {
                head { title { +"Admin Chats" } }
                body {
                    h2 { +"Чаты с пользователями" }
                    ul {
                        items.forEach { item ->
                            li {
                                a(href = "/admin/chats/${item.chatId}") {
                                    +("${item.userEmail} — ${item.lastMessage ?: "(нет сообщений)"}")
                                }
                                if (item.lastTimestamp != null) {
                                    +" [${item.lastTimestamp}]"
                                }
                            }
                        }
                    }
                    p { a(href = "/admin/products") { +"← К товарам" } }
                    p { a(href = "/admin/logout") { +"Выход" } }
                }
            }
        }



        get("/chats/{chatId}") {
            val adminId = call.requireAdminSession() ?: return@get
            val chatId = call.parameters["chatId"]?.let(UUID::fromString)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Bad chat id")

            val chatRow = transaction {
                (Chats innerJoin Users).select { Chats.id eq chatId }.singleOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound, "Chat not found")

            // Проверяем, что админ — участник чата
            if (chatRow[Chats.adminId] != adminId) {
                return@get call.respond(HttpStatusCode.Forbidden, "Нет доступа к чату")
            }

            val userEmail = chatRow[Users.email]

            val messages = transaction {
                (Messages innerJoin Users)
                    .slice(Messages.id, Messages.senderId, Messages.content, Messages.timestamp, Users.email)
                    .select { Messages.chatId eq chatId }
                    .orderBy(Messages.timestamp to SortOrder.ASC)
                    .map {
                        AdminChatMessageItem(
                            id = it[Messages.id],
                            senderEmail = it[Users.email],
                            fromAdmin = it[Messages.senderId] == adminId,
                            content = it[Messages.content],
                            timestamp = it[Messages.timestamp]
                        )
                    }
            }

            call.respondHtml {
                head { title { +"Chat: $userEmail" } }
                body {
                    h2 { +"Чат с $userEmail" }
                    messages.forEach { msg ->
                        p {
                            b {
                                +(if (msg.fromAdmin) "Админ" else msg.senderEmail)
                            }
                            +": ${msg.content} "
                            span { +"[${msg.timestamp}]" }
                        }
                    }

                    h3 { +"Отправить сообщение" }
                    form(action = "/admin/chats/$chatId", method = FormMethod.post) {
                        textInput(name = "text") { placeholder = "Сообщение..." }
                        submitInput { value = "Отправить" }
                    }

                    p { a(href = "/admin/chats") { +"← Назад к чатам" } }
                }
            }
        }


        // отправка сообщения от администратора
        post("/chats/{chatId}") {
            val adminId = call.requireAdminSession() ?: return@post
            val chatId = call.parameters["chatId"]?.let(UUID::fromString)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Bad chat id")

            val params = call.receiveParameters()
            val text = params["text"]?.trim()
            if (text.isNullOrEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Пустое сообщение")
            }

            // Проверяем, что чат принадлежит этому админу
            val valid = transaction {
                Chats.select { Chats.id eq chatId and (Chats.adminId eq adminId) }.empty().not()
            }
            if (!valid) {
                return@post call.respond(HttpStatusCode.Forbidden, "Нет доступа")
            }

            transaction {
                Messages.insert {
                    it[id] = UUID.randomUUID()
                    it[Messages.chatId] = chatId
                    it[Messages.senderId] = adminId
                    it[Messages.content] = text
                    it[Messages.timestamp] = java.time.Instant.now()
                }
            }

            call.respondRedirect("/admin/chats/$chatId")
        }


        get("/admin/logout") {
            call.sessions.clear<AdminSession>()
            call.respondRedirect("/admin/login")
        }


    }
}
