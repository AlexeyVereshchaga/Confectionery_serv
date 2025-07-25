package dto

import kotlinx.serialization.Contextual
import java.util.*
import java.time.Instant

@kotlinx.serialization.Serializable
data class AdminChatMessageItem(
    @Contextual val id: UUID,
    val senderEmail: String,
    val fromAdmin: Boolean,
    val content: String,
    @Contextual val timestamp: Instant
)