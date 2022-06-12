package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson
import no.nav.helse.sporbar.TestDatabase
import no.nav.helse.sporbar.inntektsmelding.Producer.Melding
import no.nav.helse.sporbar.uuid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InntektsmeldingStatusTest {

    private val testRapid = TestRapid()
    private val inntektsmeldingStatusDao = InntektsmeldingStatusDao(TestDatabase.dataSource)
    private val testProducer = object : Producer {
        private val meldinger = mutableListOf<Melding>()
        override fun send(melding: Melding) {
            meldinger.add(melding)
        }
        fun publisertMeldingFor(vedtaksperiodeId: UUID) = meldinger.single {
            it.json.path("vedtaksperiode").path("id").asText() == "$vedtaksperiodeId"
        }.validertJson()
        fun antallPubliserteMeldinger() = meldinger.size
        fun ingenPubliserteMeldinger () = meldinger.isEmpty()
        fun reset() = meldinger.clear()
    }

    private val mediator = InntektsmeldingStatusMediator(inntektsmeldingStatusDao, testProducer)

    init {
        TrengerInntektsmeldingRiver(testRapid, mediator)
        TrengerIkkeInntektsmeldingRiver(testRapid, mediator)
        InntektsmeldingStatusVedtaksperiodeForkastetRiver(testRapid, mediator)
        InntektsmeldingStatusVedtaksperiodeEndretRiver(testRapid, mediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        testProducer.reset()
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

    @Test
    fun `publiserer først melding når status har vært gjeldende i ett minutt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        testRapid.sendTestMessage(trengerInntektsmeldingEvent(vedtaksperiodeId))
        mediator.publiser()
        assertTrue(testProducer.ingenPubliserteMeldinger())
        manipulerMeldingInnsatt(vedtaksperiodeId, Duration.ofSeconds(59))
        mediator.publiser()
        assertTrue(testProducer.ingenPubliserteMeldinger())
        manipulerMeldingInnsatt(vedtaksperiodeId, Duration.ofSeconds(2))
        mediator.publiser()
        assertEquals(1, testProducer.antallPubliserteMeldinger())
        mediator.publiser()
        assertEquals(1, testProducer.antallPubliserteMeldinger())
    }

    @Test
    fun `publiserer ikke gammel status på inntektsmelding`() {
        val vedtaksperiodeId = UUID.randomUUID()
        testRapid.sendTestMessage(trengerIkkeInntektsmeldingEvent(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeEndretEvent(vedtaksperiodeId, "AVSLUTTET_UTEN_UTBETALING"))
        manipulerMeldingInnsatt(vedtaksperiodeId)
        mediator.publiser()
        assertEquals(1, testProducer.antallPubliserteMeldinger())
        mediator.publiser()
        assertEquals(1, testProducer.antallPubliserteMeldinger())
    }

    @Test
    fun `schema på meldinger som sendes ut`() {
        val vedtaksperiode1 = UUID.randomUUID()
        testRapid.sendTestMessage(trengerInntektsmeldingEvent(vedtaksperiode1))
        manipulerMeldingInnsatt(vedtaksperiode1)
        val vedtaksperiode2 = UUID.randomUUID()
        testRapid.sendTestMessage(trengerIkkeInntektsmeldingEvent(vedtaksperiode2))
        manipulerMeldingInnsatt(vedtaksperiode2)
        val vedtaksperiode3 = UUID.randomUUID()
        testRapid.sendTestMessage(vedtaksperiodeEndretEvent(vedtaksperiode3, "AVSLUTTET_UTEN_UTBETALING"))
        manipulerMeldingInnsatt(vedtaksperiode3)
        val vedtaksperiode4 = UUID.randomUUID()
        testRapid.sendTestMessage(vedtaksperiodeForkastetEvent(vedtaksperiode4))
        manipulerMeldingInnsatt(vedtaksperiode4)

        mediator.publiser()
        assertEquals(4, testProducer.antallPubliserteMeldinger())
        assertMeldingsinnhold(testProducer.publisertMeldingFor(vedtaksperiode1), "MANGLER_INNTEKTSMELDING")
        assertMeldingsinnhold(testProducer.publisertMeldingFor(vedtaksperiode2), "HAR_INNTEKTSMELDING")
        assertMeldingsinnhold(testProducer.publisertMeldingFor(vedtaksperiode3), "TRENGER_IKKE_INNTEKTSMELDING")
        assertMeldingsinnhold(testProducer.publisertMeldingFor(vedtaksperiode4), "BEHANDLES_UTENFOR_SPLEIS")
    }

    private fun status(vedtaksperiodeId: UUID): String? = sessionOf(TestDatabase.dataSource).use { session ->
        session.run(queryOf("SELECT status FROM inntektsmelding_status WHERE vedtaksperiode_id = ? ORDER BY melding_innsatt DESC", vedtaksperiodeId).map { it.string("status") }.asSingle)
    }

    private fun manipulerMeldingInnsatt(vedtaksperiodeId: UUID, trekkFra: Duration = Duration.ofSeconds(61)) {
        sessionOf(TestDatabase.dataSource).use { session ->
            session.run(queryOf("SELECT id FROM inntektsmelding_status WHERE vedtaksperiode_id = ?", vedtaksperiodeId).map { row -> row.uuid("id") }.asList)
        }.forEach { id ->
            @Language("PostgreSQL")
            val sql = """
                UPDATE inntektsmelding_status 
                SET melding_innsatt = (SELECT melding_innsatt FROM inntektsmelding_status WHERE id = ?) - INTERVAL '${trekkFra.seconds} SECONDS' 
                WHERE id = ?
            """
            sessionOf(TestDatabase.dataSource).use { session ->
                require(session.run(queryOf(sql, id, id).asUpdate) == 1)
            }
        }
    }

    private companion object {
        private val fnr = "12345678910"
        private val aktørId = "1427484794278"
        private val orgnr = "987654321"
        private val opprettet = LocalDateTime.parse("2022-06-10T19:06:26.765")
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

        private fun assertMeldingsinnhold(json: JsonNode, status: String) {
            assertEquals(status, json.path("status").asText())
            assertEquals(fom, LocalDate.parse(json.path("vedtaksperiode").path("fom").asText()))
            assertEquals(tom, LocalDate.parse(json.path("vedtaksperiode").path("tom").asText()))
            assertEquals(fnr, json.path("sykmeldt").asText())
            assertEquals(orgnr, json.path("arbeidsgiver").asText())
            val tidspunkt = json.path("tidspunkt").asText()
            assertEquals(opprettet, ZonedDateTime.parse(tidspunkt).toLocalDateTime())
            assertEquals(ISO_OFFSET_DATE_TIME.format(ZonedDateTime.of(opprettet, ZoneId.of("Europe/Oslo"))), tidspunkt)
        }
    }
}