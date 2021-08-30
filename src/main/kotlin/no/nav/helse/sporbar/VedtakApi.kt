package no.nav.helse.sporbar

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Route.vedtakApi(vedtakDao: VedtakDao) {
    post("/api/v1/vedtak") {
        val fnr = call.receive<List<String>>()
        call.respond(HttpStatusCode.OK, mapOf("message" to "Test"))
    }
}
