package routes

import auth.UserPrincipal
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.Products
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.util.*

fun Route.adminProductRoutes() {
    authenticate("auth-bearer") {
        route("/admin/products") {
            post {
                val principal = call.principal<UserPrincipal>()!!
                if (!principal.isAdmin) return@post call.respond(HttpStatusCode.Forbidden)

                val multipart = call.receiveMultipart()
                var name: String? = null
                var description: String? = null
                var price: BigDecimal? = null
                var imageBytes: ByteArray? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> when (part.name) {
                            "name" -> name = part.value
                            "description" -> description = part.value
                            "price" -> price = part.value.toBigDecimalOrNull()
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.streamProvider().readBytes()
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (name == null || description == null || price == null || imageBytes == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Missing fields")
                }

                transaction {
                    Products.insert {
                        it[id] = UUID.randomUUID()
                        it[Products.name] = name!!
                        it[Products.description] = description!!
                        it[Products.price] = price!!
                        it[Products.image] = ExposedBlob(imageBytes!!)
                    }
                }

                call.respond(HttpStatusCode.Created)
            }

            put("{id}") {
                val principal = call.principal<UserPrincipal>()!!
                if (!principal.isAdmin) return@put call.respond(HttpStatusCode.Forbidden)

                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                val multipart = call.receiveMultipart()
                var name: String? = null
                var description: String? = null
                var price: BigDecimal? = null
                var imageBytes: ByteArray? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> when (part.name) {
                            "name" -> name = part.value
                            "description" -> description = part.value
                            "price" -> price = part.value.toBigDecimalOrNull()
                        }
                        is PartData.FileItem -> if (part.name == "image") {
                            imageBytes = part.streamProvider().readBytes()
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                transaction {
                    Products.update({ Products.id eq id }) {
                        if (name != null) it[Products.name] = name!!
                        if (description != null) it[Products.description] = description!!
                        if (price != null) it[Products.price] = price!!
                        if (imageBytes != null) it[Products.image] = ExposedBlob(imageBytes!!)
                    }
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
