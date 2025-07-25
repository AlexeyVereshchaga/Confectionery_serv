package dto


import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class AdminChatListItem(
    @Contextual val chatId: UUID,
    val userEmail: String,
    val lastMessage: String?,
    @Contextual val lastTimestamp: Instant?
)
