package no.nav.helse.sporbar.inntektsmelding

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.TestDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InntektsmeldingStatusTest {

    private val testRapid = TestRapid()
    private val inntektsmeldingStatusDao = InntektsmeldingStatusDao(TestDatabase.dataSource)
    private val mediator = InntektsmeldingStatusMediator(inntektsmeldingStatusDao)

    init {
        TrengerInntektsmeldingRiver(testRapid, mediator)
        TrengerIkkeInntektsmeldingRiver(testRapid, mediator)
        InntektsmeldingStatusVedtaksperiodeForkastetRiver(testRapid, mediator)
        InntektsmeldingStatusVedtaksperiodeEndretRiver(testRapid, mediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `AvsluttetUtenUtbetaling via AvventerInntektsmeldingEllerHistorikk`() {
        val vedtaksperiodeId = UUID.randomUUID()
        assertNull(status(vedtaksperiodeId))
        testRapid.sendTestMessage(trengerInntektsmeldingEvent(vedtaksperiodeId))
        assertEquals("MANGLER_INNTEKTSMELDING", status(vedtaksperiodeId))
        testRapid.sendTestMessage(trengerIkkeInntektsmeldingEvent(vedtaksperiodeId))
        assertEquals("HAR_INNTEKTSMELDING", status(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeEndretEvent(vedtaksperiodeId, "AVSLUTTET_UTEN_UTBETALING"))
        assertEquals("TRENGER_IKKE_INNTEKTSMELDING", status(vedtaksperiodeId))
    }

    @Test
    fun `rett fra Start til AvventerBlokkerendePeriode`() {
        val vedtaksperiodeId = UUID.randomUUID()
        assertNull(status(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeEndretEvent(vedtaksperiodeId, "AVVENTER_BLOKKERENDE_PERIODE"))
        assertEquals("HAR_INNTEKTSMELDING", status(vedtaksperiodeId))
    }

    @Test
    fun `rett fra Start til AvsluttetUtenUtbetaling`() {
        val vedtaksperiodeId = UUID.randomUUID()
        assertNull(status(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeEndretEvent(vedtaksperiodeId, "AVSLUTTET_UTEN_UTBETALING"))
        assertEquals("TRENGER_IKKE_INNTEKTSMELDING", status(vedtaksperiodeId))
    }

    @Test
    fun `forkastet vedtaksperiode`() {
        val vedtaksperiodeId = UUID.randomUUID()
        assertNull(status(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeForkastetEvent(vedtaksperiodeId))
        assertEquals("BEHANDLES_UTENFOR_SPLEIS", status(vedtaksperiodeId))
    }

    @Test
    fun `vedtaksperiode endret til irrelevant tilstand`() {
        val vedtaksperiodeId = UUID.randomUUID()
        assertNull(status(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeEndretEvent(vedtaksperiodeId, "AVSLUTTET"))
        assertNull(status(vedtaksperiodeId))
    }

    private fun status(vedtaksperiodeId: UUID): String? = sessionOf(TestDatabase.dataSource).use { session ->
        session.run(queryOf("SELECT status FROM inntektsmelding_status WHERE vedtaksperiode_id = ? ORDER BY melding_innsatt DESC", vedtaksperiodeId).map { it.string("status") }.asSingle)
    }

    private companion object {
        private val fnr = "12345678910"
        private val aktørId = "1427484794278"
        private val orgnr = "987654321"
        private val opprettet = LocalDateTime.now()
        private val fom = LocalDate.now()
        private val tom = fom.plusDays(10)

        private fun trengerInntektsmeldingEvent(vedtaksperiodeId: UUID) = event(
            event = "trenger_inntektsmelding",
            vedtaksperiodeId = vedtaksperiodeId,
            extra = mapOf("søknadIder" to setOf(UUID.randomUUID()))
        )

        private fun trengerIkkeInntektsmeldingEvent(vedtaksperiodeId: UUID) = event(
            event = "trenger_ikke_inntektsmelding",
            vedtaksperiodeId = vedtaksperiodeId,
            extra = mapOf("søknadIder" to setOf(UUID.randomUUID()))
        )

        private fun vedtaksperiodeForkastetEvent(vedtaksperiodeId: UUID) = event(
            event = "vedtaksperiode_forkastet",
            vedtaksperiodeId = vedtaksperiodeId,
            extra = mapOf("hendelser" to setOf(UUID.randomUUID()))
        )

        private fun vedtaksperiodeEndretEvent(vedtaksperiodeId: UUID, gjeldendeTilstand: String) = event(
            event = "vedtaksperiode_endret",
            vedtaksperiodeId = vedtaksperiodeId,
            extra = mapOf("hendelser" to setOf(UUID.randomUUID()), "gjeldendeTilstand" to gjeldendeTilstand)
        )

        private fun event(
            event: String,
            vedtaksperiodeId: UUID,
            extra: Map<String, Any> = emptyMap()
        ) = JsonMessage.newMessage(
            mapOf(
                "@event_name" to event,
                "@id" to UUID.randomUUID(),
                "vedtaksperiodeId" to vedtaksperiodeId,
                "fødselsnummer" to fnr,
                "aktørId" to aktørId,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to fom,
                "tom" to tom
            ).plus(extra)
        ).toJson()
    }
}