package no.nav.helse.sporbar

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking

const val JWT_AUTH = "server_to_server"

private val client: HttpClient = HttpClient()

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

private fun fetchWellKnown(url: String) = runBlocking { client.get<JsonNode>(url) }
