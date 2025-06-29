package auth

import AdminSession
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import java.util.*

suspend fun ApplicationCall.requireAdminSession(): UUID? {
    val session = sessions.get<AdminSession>()
    if (session == null) {
        respondRedirect("/admin/login")
        return null
    }
    return session.adminId
}


