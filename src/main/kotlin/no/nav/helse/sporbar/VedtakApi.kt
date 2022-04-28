package no.nav.helse.sporbar

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

internal fun Route.vedtakApi(vedtakDao: VedtakDao) {
    post("/api/v1/vedtak") {
        val fnr = call.receive<List<String>>()
        call.respond(HttpStatusCode.OK, mapOf("message" to "Test"))
    }
}
