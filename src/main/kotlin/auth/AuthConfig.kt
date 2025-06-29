package auth

import io.ktor.server.auth.*
import io.ktor.server.auth.Authentication
import io.ktor.server.application.*
import io.ktor.server.request.*
import models.Tokens
import models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.configureAuth() {
    install(Authentication) {
        bearer("auth-bearer") {
            authenticate { tokenCredential ->
                val token = tokenCredential.token

                val userRow = transaction {
                    (Tokens innerJoin Users).select {
                        Tokens.accessToken eq token
                    }.singleOrNull()
                }

                userRow?.let {
                    UserPrincipal(
                        id = it[Users.id],
                        email = it[Users.email],
                        isAdmin = it[Users.isAdmin]
                    )
                }
            }
        }
    }
}

data class UserPrincipal(
    val id: UUID,
    val email: String,
    val isAdmin: Boolean
) : Principal
