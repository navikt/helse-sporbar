package no.nav.helse.sporbar

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

private val log = LoggerFactory.getLogger("sporbar")

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SpleisBehandling(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String,
    val utbetalingstidslinje: List<Utbetalingsdag>,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class Utbetalingsdag(
        val dato: LocalDate,
        val type: String,
        val beløpTilArbeidsgiver: Int,
        val beløpTilBruker: Int,
        val sykdomsgrad: Int,
        val dekningsgrad: Int,
        val begrunnelser: List<String>,
        val forespurtRefusjon: ForespurtRefusjon,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ForespurtRefusjon(
        val beløp: Int,
        val avsender: String,
        val sasInternInntektsmeldingReferanse: UUID?, // kun satt hvis avsender er ARBEIDSGIVER
    )
}

internal class SpleisClient(
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    private val baseUrl: String,
    private val scope: String,
) {
    internal fun hentBehandling(
        fødselsnummer: String,
        behandlingId: UUID,
        callId: String,
    ): SpleisBehandling {
        val body = objectMapper.writeValueAsString(mapOf("fødselsnummer" to fødselsnummer, "behandlingId" to behandlingId))
        val request =
            HttpRequest
                .newBuilder(URI("$baseUrl/api/behandling"))
                .header("Authorization", "Bearer ${tokenProvider.bearerToken(scope).getOrThrow().token}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("callId", callId)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (val status = response.statusCode()) {
            200 -> objectMapper.readValue(response.body())

            else -> {
                // Responsen kan inneholde personopplysninger, så vi logger bare status og id-er
                log.error("Feil ved henting av behandling {} fra spleis-api: status={} callId={}", behandlingId, status, callId)
                error("Feil ved henting av behandling fra spleis-api: status=$status")
            }
        }
    }
}
