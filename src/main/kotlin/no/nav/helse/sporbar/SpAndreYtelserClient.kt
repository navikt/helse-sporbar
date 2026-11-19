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
internal data class GraderteAndreYtelserPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val ytelser: List<YtelseMedGrad>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YtelseMedGrad(
    val type: String,
    val grad: Int,
)

internal class SpAndreYtelserClient(
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    private val baseUrl: String,
    private val scope: String,
) {
    internal fun hentPerioder(
        versjon: UUID,
        fom: LocalDate,
        tom: LocalDate,
        callId: String,
    ): List<GraderteAndreYtelserPeriode> {
        val request =
            HttpRequest
                .newBuilder(URI("$baseUrl/api/graderte-andre-ytelser/versjoner/$versjon/perioder?fom=$fom&tom=$tom"))
                .header("Authorization", "Bearer ${tokenProvider.bearerToken(scope).getOrThrow().token}")
                .header("Accept", "application/json")
                .header("callId", callId)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (val status = response.statusCode()) {
            200 -> objectMapper.readValue(response.body())

            else -> {
                log.error("Feil ved henting av graderte andre ytelser for versjon {} fra sp-andre-ytelser: status={} callId={}", versjon, status, callId)
                error("Feil ved henting av graderte andre ytelser fra sp-andre-ytelser: status=$status")
            }
        }
    }
}
