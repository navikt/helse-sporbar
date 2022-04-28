package no.nav.helse.sporbar

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import kotlinx.coroutines.runBlocking

const val JWT_AUTH = "server_to_server"

private val client: HttpClient = HttpClient(Apache) {
    engine {
        customizeClient {
            useSystemProperties()
        }
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
}

fun Application.azureAdAppAuthentication(wellKnownUrl: String, clientId: String) {
    val wellKnown = fetchWellKnown(wellKnownUrl)
    val issuer = wellKnown["issuer"].textValue()
    val jwksUrl = wellKnown["jwks_uri"].textValue()
    install(Authentication) {
        jwt(JWT_AUTH) {
            verifier(JwkProviderBuilder(jwksUrl).build(), issuer) {
                withAudience(clientId)
            }

            validate { credentials -> JWTPrincipal(credentials.payload) }
        }
    }
}

private fun fetchWellKnown(url: String) = runBlocking { client.prepareGet(url) {
    accept(ContentType.Application.Json)
}.body<JsonNode>() }
