import auth.configureAuth
import database.DatabaseFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import routes.adminHtmlRoutes
import routes.authRoutes
import routes.chatRoutes
import routes.productRoutes
import java.util.*


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

data class AdminSession(val adminId: UUID)

fun Application.module() {
    install(Sessions) {
        cookie<AdminSession>("admin_session") {
            cookie.path = "/"
            cookie.httpOnly = true
        }
    }

    install(CallLogging)
    install(ContentNegotiation) {
        json()
    }
    install(Authentication)

    DatabaseFactory.init()

    configureAuth()
    configureRoutes()
}

fun Application.configureRoutes() {
    routing {
        authRoutes()
        productRoutes()
        chatRoutes()
        adminHtmlRoutes()
        // TODO: adminHtmlRoutes() и т.д.
    }
}


