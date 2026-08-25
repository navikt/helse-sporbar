package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.slf4j.LoggerFactory

private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

internal data class Forsikringsvurdering(
    val individuellForsikringNavn: String?,
    val kollektivForsikringNavn: String?,
    val dekning: Dekning?,
)

internal data class Dekning(
    val grad: Int,
    val fraDag: Int,
)

internal class SpForsikringClient(
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    private val baseUrl: String,
    private val scope: String,
) {
    internal fun hentForsikringsvurdering(
        forsikringsvurderingId: UUID,
        callId: String,
    ): Forsikringsvurdering {
        val request =
            HttpRequest
                .newBuilder(URI("$baseUrl/forsikringsvurderinger/$forsikringsvurderingId"))
                .header("Authorization", "Bearer ${tokenProvider.bearerToken(scope).getOrThrow().token}")
                .header("Accept", "application/json")
                .header("callId", callId)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (val status = response.statusCode()) {
            200 -> objectMapper.readTree(response.body()).tilForsikringsvurdering()

            else -> {
                sikkerLogg.error("Feil ved henting av forsikringsvurdering $forsikringsvurderingId: status=$status body=${response.body()}")
                error("Feil ved henting av forsikringsvurdering fra sp-forsikring: status=$status")
            }
        }
    }

    private fun JsonNode.tilForsikringsvurdering() =
        Forsikringsvurdering(
            individuellForsikringNavn =
                get("individuelleForsikringer")
                    .firstOrNull { it["lagtTilGrunn"].asBoolean() }
                    ?.get("navn")
                    ?.textValue(),
            kollektivForsikringNavn =
                get("kollektivForsikring")
                    .takeUnless { it.isNull }
                    ?.get("navn")
                    ?.textValue(),
            dekning =
                get("samletDekning")
                    .takeUnless { it.isNull }
                    ?.let {
                        Dekning(
                            grad = it["grad"].asInt(),
                            fraDag = it["fraDag"].asInt(),
                        )
                    },
        )
}
