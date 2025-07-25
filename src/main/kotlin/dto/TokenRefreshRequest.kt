package dto

import kotlinx.serialization.Serializable


/**
 * 🔹 Запрос
 * http
 * Копировать
 * Редактировать
 * POST /auth/refresh
 * Content-Type: application/json
 *
 * {
 *     "refreshToken": "some-refresh-token"
 * }
 */
@Serializable
data class TokenRefreshRequest(
    val refreshToken: String
)


/**
 * {
 *   "accessToken": "new-access-token",
 *   "refreshToken": "new-refresh-token"
 * }
 *
 */
@Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String
)