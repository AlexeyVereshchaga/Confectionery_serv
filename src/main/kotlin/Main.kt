import auth.configureAuth
import database.DatabaseFactory
import dto.InstantIso8601Serializer
import dto.UUIDSerializer
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
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.http.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant


fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module)
        .start(wait = true)
}

data class AdminSession(val adminId: UUID)

data class UserSession(val userId: UUID)

fun Application.module() {
    install(Sessions) {
        cookie<AdminSession>("admin_session") {
            cookie.path = "/"
            cookie.httpOnly = true
        }
        cookie<UserSession>("user_session")
    }

    install(CallLogging)
    install(ContentNegotiation) {
        json(Json {
            serializersModule = SerializersModule {
                contextual(UUID::class, UUIDSerializer)
                contextual(Instant::class, InstantIso8601Serializer)
            }
            encodeDefaults = true
            ignoreUnknownKeys = true
        })
    }

    configureStatusPages()
    DatabaseFactory.init()

    configureAuth()
    configureRoutes()
}

fun Application.configureRoutes() {
    routing {
        get("/") {
            call.respondText("Сервер работает!", ContentType.Text.Plain)
        }
        authRoutes()
        productRoutes()
        chatRoutes()
        adminHtmlRoutes()
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { statusCode ->
            call.respondText("Страница не найдена", status = statusCode)
        }
        status(HttpStatusCode.Unauthorized) { statusCode ->
            call.respondText("Доступ запрещён", status = statusCode)
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respondText("Ошибка сервера: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }
}



