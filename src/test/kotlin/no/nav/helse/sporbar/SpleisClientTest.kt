package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.ok
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class SpleisClientTest {
    private val fødselsnummer = "12345678910"
    private val behandlingId = UUID.randomUUID()
    private val kildeId = UUID.randomUUID()
    private val request = slot<HttpRequest>()
    private val httpClient = mockk<HttpClient>()
    private val tokenProvider =
        mockk<AzureTokenProvider> {
            every { bearerToken("scope") } returns AzureToken("token", LocalDateTime.now().plusHours(1)).ok()
        }
    private val client = SpleisClient(httpClient, objectMapper, tokenProvider, "http://spleis-api", "scope")

    @Test
    fun `parser behandling fra spleis-api`() {
        svar(
            200,
            """
            {
              "vedtaksperiodeId": "${UUID.randomUUID()}",
              "behandlingId": "$behandlingId",
              "organisasjonsnummer": "987654321",
              "yrkesaktivitetstype": "ARBEIDSTAKER",
              "utbetalingstidslinje": [
                {
                  "dato": "2018-01-17",
                  "type": "NavDag",
                  "beløpTilArbeidsgiver": 1431,
                  "beløpTilBruker": 0,
                  "sykdomsgrad": 100,
                  "dekningsgrad": 100,
                  "begrunnelser": [],
                  "forespurtRefusjon": { "avsender": "ARBEIDSGIVER", "sasInternInntektsmeldingReferanse": "$kildeId", "beløp": 1431 }
                },
                {
                  "dato": "2018-01-18",
                  "type": "AvvistDag",
                  "beløpTilArbeidsgiver": 0,
                  "beløpTilBruker": 0,
                  "sykdomsgrad": 10,
                  "dekningsgrad": 100,
                  "begrunnelser": ["MinimumSykdomsgrad"],
                      "forespurtRefusjon": { "avsender": "SAKSBEHANDLER", "sasInternInntektsmeldingReferanse": null, "beløp": 1431 }
                }
              ],
              "ukjentFelt": true
            }
            """,
        )

        val behandling = client.hentBehandling(fødselsnummer, behandlingId, "callId")

        assertEquals(behandlingId, behandling.behandlingId)
        val (navDag, avvistDag) = behandling.utbetalingstidslinje
        assertEquals(LocalDate.of(2018, 1, 17), navDag.dato)
        assertEquals(1431, navDag.beløpTilArbeidsgiver)
        assertEquals(1431, navDag.forespurtRefusjon.beløp)
        assertEquals(SpleisBehandling.ForespurtRefusjon(1431, "ARBEIDSGIVER", kildeId), navDag.forespurtRefusjon)
        assertEquals(listOf("MinimumSykdomsgrad"), avvistDag.begrunnelser)
        assertNull(avvistDag.forespurtRefusjon.sasInternInntektsmeldingReferanse)
        assertEquals("http://spleis-api/api/behandling", request.captured.uri().toString())
        assertEquals("POST", request.captured.method())
    }

    @Test
    fun `feil fra spleis-api gir exception uten personopplysninger`() {
        listOf(404, 500).forEach { status ->
            svar(status, """{"fødselsnummer":"$fødselsnummer"}""")

            val feil = assertThrows<IllegalStateException> { client.hentBehandling(fødselsnummer, behandlingId, "callId") }

            assertEquals("Feil ved henting av behandling fra spleis-api: status=$status", feil.message)
            assertFalse(feil.message!!.contains(fødselsnummer))
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
