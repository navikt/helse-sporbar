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
        val TIDSSTEMPEL = LocalDateTime.now()
        val FOM = LocalDate.of(2020, 1, 1)
        val TOM = LocalDate.of(2020, 1, 31)
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


    }


    @Language("json")
    private fun vedtakFattetUtenUtbetaling() = """{
  "vedtaksperiodeId": "3af72a48-3944-4256-a224-e52feb1a5540",
  "fom": "$FOM",
  "tom": "$TOM",
  "hendelser": [
    "08a433b7-4749-4fc4-b24f-ca3ede32041e",
    "af6f884f-8b9f-4b67-ba15-444d673898cf",
    "8cfb8541-6367-4d01-8d80-655e231d6694"
  ],
  "sykepengegrunnlag": 388260.0,
  "inntekt": 32355.0,
  "@event_name": "vedtak_fattet",
  "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec04",
  "@opprettet": "$TIDSSTEMPEL",
  "aktørId": "qwerty",
  "fødselsnummer": "$FØDSELSNUMMER",
  "organisasjonsnummer": "$ORGNUMMER"
}
    """
}

