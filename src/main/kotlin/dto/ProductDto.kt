package dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import models.Products
import org.jetbrains.exposed.sql.ResultRow
import java.util.UUID


@Serializable
data class ProductDto(
    @Contextual val id: UUID,
    val name: String,
    val description: String,
    val price: Double,
    val formattedPrice: String,
    val imageUrl: String
)

fun ResultRow.toProductDto() = ProductDto(
    id = this[Products.id],
    name = this[Products.name],
    description = this[Products.description],
    price = this[Products.price].toDouble(),
    formattedPrice = this[Products.price].toPlainString(),
    imageUrl = "/products/${this[Products.id]}/image"
)