package routes

import auth.UserPrincipal
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import models.Products
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.math.BigDecimal

@Serializable
data class ProductResponse(
    @Contextual val id: UUID,
    val name: String,
    val description: String,
    @Contextual val price: BigDecimal,
    val imageBase64: String
)

fun Route.productRoutes() {
    authenticate("auth-bearer") {
        route("/products") {
            // Список всех товаров (для всех пользователей)
            get {
                val products = transaction {
                    Products.selectAll().map {
                        ProductResponse(
                            id = it[Products.id],
                            name = it[Products.name],
                            description = it[Products.description],
                            price = it[Products.price],
                            imageBase64 = Base64.getEncoder().encodeToString(it[Products.image].bytes)
                        )
                    }
                }
                call.respond(products)
            }

            // Получение конкретного товара
            get("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val product = transaction {
                    Products.select { Products.id eq id }
                        .map {
                            ProductResponse(
                                id = it[Products.id],
                                name = it[Products.name],
                                description = it[Products.description],
                                price = it[Products.price],
                                imageBase64 = Base64.getEncoder().encodeToString(it[Products.image].bytes)
                            )
                        }.singleOrNull()
                }

                if (product == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(product)
            }

            // Создание товара (только администратор)
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
                        is PartData.FormItem -> {
                            when (part.name) {
                                "name" -> name = part.value
                                "description" -> description = part.value
                                "price" -> price = part.value.toBigDecimalOrNull()
                            }
                        }

                        is PartData.FileItem -> {
                            if (part.name == "image") {
                                imageBytes = part.streamProvider().readBytes()
                            }
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

            // Редактирование товара (только администратор)
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
                        is PartData.FormItem -> {
                            when (part.name) {
                                "name" -> name = part.value
                                "description" -> description = part.value
                                "price" -> price = part.value.toBigDecimalOrNull()
                            }
                        }

                        is PartData.FileItem -> {
                            if (part.name == "image") {
                                imageBytes = part.streamProvider().readBytes()
                            }
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
