package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class VedtakFattetRiverTest {

    companion object {
        val FØDSELSNUMMER = "12345678910"
        val ORGNUMMER = "123456789"
        val VEDTAKSPERIODEID = "3af72a48-3944-4256-a224-e52feb1a5540"
        val AKTØRID = "qwerty"
        val TIDSSTEMPEL = LocalDateTime.now()
        val FOM = LocalDate.of(2020, 1, 1)
        val TOM = LocalDate.of(2020, 1, 31)
        val SKJÆRINGSTIDSPUNKT = LocalDate.of(2020, 1, 1)
        val SYKEPENGEGRUNNLAG = 388260.0
        val INNTEKT = 388260.0
    }

    private val testRapid = TestRapid()
    private val producerMock = mockk<KafkaProducer<String,JsonNode>>(relaxed = true)
    private val dataSource = setUpDatasourceWithFlyway()
    private val dokumentDao = DokumentDao(dataSource)

    private val vedtakFattetMediator = VedtakFattetMediator(
        dokumentDao = dokumentDao,
        producer = producerMock
    )

    init {
        VedtakFattetRiver(testRapid, vedtakFattetMediator)
    }

    @AfterAll
    fun cleanUp() {
        dataSource.close()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `vanlig vedtakFattet uten utbetaling`() {
        val captureSlot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        testRapid.sendTestMessage(vedtakFattetUtenUtbetaling())

        verify { producerMock.send( capture(captureSlot) ) }

        val vedtakFattet = captureSlot.captured
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.value()
        assertEquals(vedtakFattetJson["fødselsnummer"].textValue(), FØDSELSNUMMER)
        assertEquals(vedtakFattetJson["aktørId"].textValue(), AKTØRID)
        assertEquals(vedtakFattetJson["fom"].asLocalDate(), FOM)
        assertEquals(vedtakFattetJson["tom"].asLocalDate(), TOM)
        assertEquals(vedtakFattetJson["skjæringstidspunkt"].asLocalDate(), SKJÆRINGSTIDSPUNKT)
        assertEquals(vedtakFattetJson["inntekt"].asDouble(), INNTEKT)
        assertEquals(vedtakFattetJson["sykepengegrunnlag"].asDouble(), SYKEPENGEGRUNNLAG)
        assertTrue(vedtakFattetJson.path("utbetalingId").let { it.isMissingNode || it.isNull })
        assertTrue(vedtakFattetJson.path("vedtaksperiodeId").let { it.isMissingNode || it.isNull })
    }

    @Test
    fun `vedtakFattet med utbetaling`() {
        val captureSlot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling())

        verify { producerMock.send( capture(captureSlot) ) }

        val vedtakFattet = captureSlot.captured
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.value()
        assertEquals(vedtakFattetJson["fødselsnummer"].textValue(), FØDSELSNUMMER)
        assertEquals(vedtakFattetJson["fom"].asLocalDate(), FOM)
        assertEquals(vedtakFattetJson["tom"].asLocalDate(), TOM)
        assertEquals(vedtakFattetJson["skjæringstidspunkt"].asLocalDate(), SKJÆRINGSTIDSPUNKT)
        assertTrue(vedtakFattetJson.path("utbetalingId").asText().isNotEmpty())
    }

    @Language("json")
    private fun vedtakFattetUtenUtbetaling() = """{
  "vedtaksperiodeId": "$VEDTAKSPERIODEID",
  "fom": "$FOM",
  "tom": "$TOM",
  "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
  "hendelser": [
    "08a433b7-4749-4fc4-b24f-ca3ede32041e",
    "af6f884f-8b9f-4b67-ba15-444d673898cf",
    "8cfb8541-6367-4d01-8d80-655e231d6694"
  ],
  "sykepengegrunnlag": "$SYKEPENGEGRUNNLAG",
  "inntekt": "$INNTEKT",
  "@event_name": "vedtak_fattet",
  "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec04",
  "@opprettet": "$TIDSSTEMPEL",
  "aktørId": "$AKTØRID",
  "fødselsnummer": "$FØDSELSNUMMER",
  "organisasjonsnummer": "$ORGNUMMER"
}
    """

    @Language("json")
    private fun vedtakFattetMedUtbetaling() = """{
  "vedtaksperiodeId": "$VEDTAKSPERIODEID",
  "fom": "$FOM",
  "tom": "$TOM",
  "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
  "hendelser": [
    "08a433b7-4749-4fc4-b24f-ca3ede32041e",
    "af6f884f-8b9f-4b67-ba15-444d673898cf",
    "8cfb8541-6367-4d01-8d80-655e231d6694"
  ],
  "sykepengegrunnlag": "$SYKEPENGEGRUNNLAG",
  "inntekt": "$INNTEKT",
  "utbetalingId": "41425537-24c0-494d-8fc6-5decf0ae7ecb",
  "@event_name": "vedtak_fattet",
  "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec04",
  "@opprettet": "$TIDSSTEMPEL",
  "aktørId": "$AKTØRID",
  "fødselsnummer": "$FØDSELSNUMMER",
  "organisasjonsnummer": "$ORGNUMMER"
}
    """
}

