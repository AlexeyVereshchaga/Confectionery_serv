package auth


import kotlinx.datetime.Clock
import java.util.*
import models.Tokens
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlin.time.Duration.Companion.hours
import java.sql.Timestamp



object TokenService {
    fun generateAccessToken(): String = UUID.randomUUID().toString()
    fun generateRefreshToken(): String = UUID.randomUUID().toString()
    fun now(): Instant = Clock.System.now()

    fun generateTokensForUser(userId: UUID): Pair<String, String> {


        val accessToken = generateAccessToken()
        val refreshToken = generateRefreshToken()
        val expiresAt = now().plus(1.hours)

        transaction {
            Tokens.insert {
                it[id] = UUID.randomUUID()
                it[Tokens.userId] = userId
                it[Tokens.accessToken] = accessToken
                it[Tokens.refreshToken] = refreshToken
                it[Tokens.expiresAt] = expiresAt.toJavaInstant()
            }
        }

        return accessToken to refreshToken
    }
}
