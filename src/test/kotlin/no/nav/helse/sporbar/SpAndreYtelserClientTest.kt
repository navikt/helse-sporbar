package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.ok
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class SpAndreYtelserClientTest {
    private val versjon = UUID.randomUUID()
    private val fom = LocalDate.of(2018, 1, 1)
    private val tom = LocalDate.of(2018, 1, 31)
    private val request = slot<HttpRequest>()
    private val httpClient = mockk<HttpClient>()
    private val tokenProvider =
        mockk<AzureTokenProvider> {
            every { bearerToken("scope") } returns AzureToken("token", LocalDateTime.now().plusHours(1)).ok()
        }
    private val client = SpAndreYtelserClient(httpClient, objectMapper, tokenProvider, "http://sp-andre-ytelser", "scope")

    @Test
    fun `henter perioder med graderte andre ytelser for vedtaket`() {
        svar(
            200,
            """
            [
              {
                "fom": "2018-01-03",
                "tom": "2018-01-09",
                "ytelser": [
                  { "type": "FORELDREPENGER", "grad": 40 },
                  { "type": "OMSORGSPENGER", "grad": 20 }
                ]
              },
              {
                "fom": "2018-01-15",
                "tom": "2018-01-15",
                "ytelser": [{ "type": "PLEIEPENGER", "grad": 50 }]
              }
            ]
            """,
        )

        val perioder = client.hentPerioder(versjon, fom, tom, "callId")

        assertEquals(
            listOf(
                GraderteAndreYtelserPeriode(
                    fom = LocalDate.of(2018, 1, 3),
                    tom = LocalDate.of(2018, 1, 9),
                    ytelser = listOf(YtelseMedGrad("FORELDREPENGER", 40), YtelseMedGrad("OMSORGSPENGER", 20)),
                ),
                GraderteAndreYtelserPeriode(
                    fom = LocalDate.of(2018, 1, 15),
                    tom = LocalDate.of(2018, 1, 15),
                    ytelser = listOf(YtelseMedGrad("PLEIEPENGER", 50)),
                ),
            ),
            perioder,
        )
        assertEquals(
            "http://sp-andre-ytelser/api/graderte-andre-ytelser/versjoner/$versjon/perioder?fom=2018-01-01&tom=2018-01-31",
            request.captured.uri().toString(),
        )
        assertEquals("GET", request.captured.method())
    }

    @Test
    fun `feil fra sp-andre-ytelser gir exception`() {
        listOf(404, 500).forEach { status ->
            svar(status, """{"title":"Fant ikke versjonen"}""")

            val feil = assertThrows<IllegalStateException> { client.hentPerioder(versjon, fom, tom, "callId") }

            assertEquals("Feil ved henting av graderte andre ytelser fra sp-andre-ytelser: status=$status", feil.message)
        }
    }

    private fun svar(
        status: Int,
        body: String,
    ) {
        val response =
            mockk<HttpResponse<String>> {
                every { statusCode() } returns status
                every { body() } returns body
            }
        every { httpClient.send(capture(request), any<HttpResponse.BodyHandler<String>>()) } returns response
    }
}
