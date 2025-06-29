package routes

import AdminSession
import auth.PasswordHasher
import auth.requireAdminSession
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.*
import models.Products
import models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
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

    }
}
