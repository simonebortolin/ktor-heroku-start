package com.simonebortolin.ktor.heroku

import com.fasterxml.jackson.databind.SerializationFeature
import com.zaxxer.hikari.*
import freemarker.cache.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.freemarker.*
import io.ktor.html.respondHtml
import io.ktor.http.*
import io.ktor.http.content.file
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.http.content.staticRootFolder
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.*
import kotlinx.html.*
import kotlinx.css.*
import kotlinx.css.properties.*
import java.io.File

val hikariConfig = HikariConfig().apply {
    jdbcUrl = System.getenv("JDBC_DATABASE_URL")
}

val dataSource = if (hikariConfig.jdbcUrl != null)
    HikariDataSource(hikariConfig)
else
    HikariDataSource()

val html_utf8 = ContentType.Text.Html.withCharset(Charsets.UTF_8)

fun Application.module() {
    install(DefaultHeaders)
    install(ConditionalHeaders)
    install(PartialContent)

    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(environment.classLoader, "templates")
    }

    install(StatusPages) {
        exception<Exception> { exception ->
            call.respond(FreeMarkerContent("error.ftl", exception, "", html_utf8))
        }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    routing {

        get("hello") {
            call.respond("Hello World")
        }

        get("error") {
            throw IllegalStateException("An invalid place to be …")
        }

        get("/") {
            val model = HashMap<String, Any>()
            model.put("message", "Hello World!")
            val etag = model.toString().hashCode().toString()
            call.respond(FreeMarkerContent("index.ftl", model, etag, html_utf8))
        }

        get("/kotlinxhtml") {
            call.respondHtml {
                head {
                    title { +"Async World" }
                }
                body {
                    h1 {
                        id = "title"
                        +"Title"
                    }
                    div {
                        id = "div"
                        +"Hello World"
                    }
                    div {
                        id = "ktor-kotlinx"
                        +"Ktor Kotlinx"
                    }
                }
            }
        }

        get("/db") {
            val model = HashMap<String, Any>()
            dataSource.connection.use { connection ->
                val rs = connection.createStatement().run {
                    executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)")
                    executeUpdate("INSERT INTO ticks VALUES (now())")
                    executeQuery("SELECT tick FROM ticks")

                }

                val output = ArrayList<String>()
                while (rs.next()) {
                    output.add("Read from DB: " + rs.getTimestamp("tick"))
                }
                model.put("results", output)
            }

            val etag = model.toString().hashCode().toString()
            call.respond(FreeMarkerContent("db.ftl", model, etag, html_utf8))
        }

        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.red
                }
                p {
                    fontSize = 2.em
                }
                rule("p.myclass") {
                    color = Color.blue
                }
            }
        }

        get("/json/jackson") {
            call.respond(mapOf("hello" to "world"))
        }

        static("static") {
            staticRootFolder = File("public")

            file("lang-logo.png")
        }

        static("stylesheets") {
            files("public/stylesheets")
        }

    }


}

fun main(args: Array<String>) {
    val port = try {
        Integer.valueOf(System.getenv("PORT"))
    } catch (e: Exception) {
        9999
    }
    embeddedServer(Netty, port, module = Application::module).start()
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}