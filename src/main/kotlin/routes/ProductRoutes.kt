package routes

import dto.toProductDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.Products
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Route.productRoutes() {
    authenticate("auth-bearer") {
        route("/products") {
            // список продуктов
            get {
                val products = transaction {
                    Products.selectAll().map { it.toProductDto() }
                }
                call.respond(products)
            }

            // продукт по id
            get("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val product = transaction {
                    Products.select { Products.id eq id }
                        .map { it.toProductDto() }
                        .singleOrNull()
                }

                if (product == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(product)
            }

            // картинка продукта
            get("{id}/image") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val imageBytes = transaction {
                    Products
                        .slice(Products.image)
                        .select { Products.id eq id }
                        .map { it[Products.image].bytes } // убрал nullable
                        .singleOrNull()
                }

                if (imageBytes == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respondBytes(imageBytes, ContentType.Image.JPEG)
                }
            }
        }
    }
}
