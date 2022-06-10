package no.nav.helse.sporbar.inntektsmelding

import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.TestDatabase
import no.nav.helse.sporbar.vedtaksperiodeForkastet.VedtaksperiodeForkastetDao
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class InntektsmeldingTest {

    private val testRapid = TestRapid()
    private val fnr = "12345678910"
    private val aktørId = "1427484794278"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()

    private val inntektsmeldingDao = InntektsmeldingDao(TestDatabase.dataSource)
    private val vedtaksperiodeForkastetDao = VedtaksperiodeForkastetDao(TestDatabase.dataSource)
    private val mediator = InntektsmeldingStatusMediator(inntektsmeldingDao, vedtaksperiodeForkastetDao, mockk(relaxed = true))

    init {
        TrengerInntektsmeldingRiver(testRapid, mediator)
        TrengerIkkeInntektsmeldingRiver(testRapid, mediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `Kan lese trenger_inntektsmelding og trenger_ikke_inntektsmelding og lagre i db`() {
        val vedtaksperiodeId = UUID.randomUUID()

        assertFalse(finnTrengerInntektsmelding(vedtaksperiodeId))
        assertFalse(finnTrengerIkkeInntektsmelding(vedtaksperiodeId))

        testRapid.sendTestMessage(eventSomJson("trenger_inntektsmelding", vedtaksperiodeId))
        testRapid.sendTestMessage(eventSomJson("trenger_ikke_inntektsmelding", vedtaksperiodeId))

        assertTrue(finnTrengerInntektsmelding(vedtaksperiodeId))
        assertTrue(finnTrengerInntektsmelding(vedtaksperiodeId))
    }

    @Test
    fun `Dedup på hendelseId i tilfelle man må spille av på nytt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()

        assertDoesNotThrow {
            testRapid.sendTestMessage(eventSomJson("trenger_inntektsmelding", vedtaksperiodeId, hendelseId))
            testRapid.sendTestMessage(eventSomJson("trenger_inntektsmelding", vedtaksperiodeId, hendelseId))
        }
    }


    private fun eventSomJson(
        event: String = "trenger_inntektsmelding",
        vedtaksperiodeId: UUID,
        hendelseId: UUID = UUID.randomUUID()
    ): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to event,
                "@id" to hendelseId,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "fødselsnummer" to fnr,
                "aktørId" to aktørId,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to LocalDate.now(),
                "tom" to LocalDate.now().plusDays(10),
                "søknadIder" to setOf(UUID.randomUUID())
            )
        ).toJson()
    }

    private fun finnTrengerInntektsmelding(vedtaksperiodeId: UUID) =
        finn(vedtaksperiodeId, tabellNavn = "trenger_inntektsmelding")

    private fun finnTrengerIkkeInntektsmelding(vedtaksperiodeId: UUID) =
        finn(vedtaksperiodeId, tabellNavn = "trenger_ikke_inntektsmelding")

    private fun finn(vedtaksperiodeId: UUID, tabellNavn: String): Boolean {
        return sessionOf(TestDatabase.dataSource).use { session ->
            session.run(queryOf("SELECT COUNT(1) FROM $tabellNavn WHERE vedtaksperiode_id = ?", vedtaksperiodeId).map { it.int(1) > 0 }.asSingle)
        } ?: false
    }
}
