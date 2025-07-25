package routes

import auth.PasswordHasher
import auth.TokenService
import dto.TokenRefreshRequest
import dto.TokenRefreshResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import models.Tokens
import models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

@Serializable
data class RegisterRequest(val email: String, val password: String, val isAdmin: Boolean)

@Serializable
data class TokenRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String)

fun Route.authRoutes() {
    route("/register") {
        post {
            val body = call.receive<RegisterRequest>()
            val exists = transaction {
                Users.select { Users.email eq body.email }.count() > 0
            }

            if (exists) {
                call.respondText("User already exists", status = io.ktor.http.HttpStatusCode.Conflict)
                return@post
            }

            val userId = UUID.randomUUID()

            transaction {
                Users.insert {
                    it[id] = userId
                    it[email] = body.email
                    it[passwordHash] = PasswordHasher.hash(body.password)
                    it[isAdmin] = body.isAdmin
                }
            }

            val (access, refresh) = TokenService.generateTokensForUser(userId)
            call.respond(TokenResponse(access, refresh))
        }
    }

    route("/token") {
        post {
            val body = call.receive<TokenRequest>()
            val user = transaction {
                Users.select { Users.email eq body.email }.singleOrNull()
            }

            if (user == null || !PasswordHasher.verify(body.password, user[Users.passwordHash])) {
                call.respondText("Invalid credentials", status = io.ktor.http.HttpStatusCode.Unauthorized)
                return@post
            }

            val userId = user[Users.id]
            val (access, refresh) = TokenService.generateTokensForUser(userId)
            call.respond(TokenResponse(access, refresh))
        }
    }

    route("/refresh") {
        post {
            val body = call.receive<RefreshRequest>()

            val userId = transaction {
                Tokens.select { Tokens.refreshToken eq body.refreshToken }
                    .orderBy(Tokens.expiresAt, SortOrder.DESC)
                    .limit(1)
                    .map { it[Tokens.userId] }
                    .firstOrNull()
            }

            if (userId == null) {
                call.respondText("Invalid refresh token", status = io.ktor.http.HttpStatusCode.Unauthorized)
                return@post
            }

            val (access, refresh) = TokenService.generateTokensForUser(userId)
            call.respond(TokenResponse(access, refresh))
        }
    }

    post("/logout") {
        val token = call.request.header("Authorization")?.removePrefix("Bearer ")
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Нет access токена")

        val deleted = transaction {
            Tokens.deleteWhere { Tokens.accessToken eq token }
        }

        if (deleted > 0) {
            call.respond(HttpStatusCode.OK, "Вы вышли из системы")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Недействительный токен")
        }
    }


    post("/refresh") {
        val request = call.receive<TokenRefreshRequest>()

        val tokenRow = transaction {
            Tokens.select { Tokens.refreshToken eq request.refreshToken }.singleOrNull()
        }

        if (tokenRow == null) {
            call.respond(HttpStatusCode.Unauthorized, "Неверный refresh token")
            return@post
        }

        val expiresAt = tokenRow[Tokens.expiresAt]
        val now = java.time.Instant.now()

        if (expiresAt.isBefore(now)) {
            call.respond(HttpStatusCode.Unauthorized, "Refresh token истёк")
            return@post
        }

        val userId = tokenRow[Tokens.userId]

        // По желанию: удалить старый refresh token (если нужен 1 активный токен)
        transaction {
            Tokens.deleteWhere { Tokens.refreshToken eq request.refreshToken }
        }

        val (newAccessToken, newRefreshToken) = TokenService.generateTokensForUser(userId)

        call.respond(
            TokenRefreshResponse(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken
            )
        )
    }


}
