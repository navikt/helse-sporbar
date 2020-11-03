package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AnnulleringRiverTest {

    companion object {
        val fødselsnummer = "12345678910"
        val orgnummer = "123456789"
        val tidsstempel = LocalDateTime.now()
        val fom = LocalDate.now()
        val tom = LocalDate.now()
    }

    private val testRapid = TestRapid()
    private val producerMock = mockk<KafkaProducer<String,JsonNode>>(relaxed = true)

    init {
        AnnulleringRiver(testRapid, producerMock)
    }

    @Test
    fun `vanlig annullering`() {
        testRapid.sendTestMessage(annullering())

        val captureSlot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producerMock.send( capture(captureSlot) ) }

        val annullering = captureSlot.captured
        assertEquals(fødselsnummer, annullering.key())

        val annulleringJson = annullering.value()
        assertEquals(annulleringJson["fødselsnummer"].textValue(), fødselsnummer)
        assertEquals(annulleringJson["orgnummer"].textValue(), orgnummer)
        assertEquals(annulleringJson["tidsstempel"].asLocalDateTime(), tidsstempel)
        assertEquals(annulleringJson["fom"].asLocalDate(), fom)
        assertEquals(annulleringJson["tom"].asLocalDate(), tom)
    }

    @Test
    fun `tomme utbetalingslinjer`() {
        testRapid.sendTestMessage(annullering(emptyList()))

        val captureSlot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producerMock.send( capture(captureSlot) ) }

        val annullering = captureSlot.captured
        assertEquals(fødselsnummer, annullering.key())

        val annulleringJson = annullering.value()
        assertEquals(annulleringJson["fødselsnummer"].textValue(), fødselsnummer)
        assertEquals(annulleringJson["orgnummer"].textValue(), orgnummer)
        assertEquals(annulleringJson["tidsstempel"].asLocalDateTime(), tidsstempel)
        assertEquals(annulleringJson["fom"].asOptionalLocalDate(), null)
        assertEquals(annulleringJson["tom"].asOptionalLocalDate(), null)
    }

    @Language("json")
    private fun annullering(utbetalingslinjer: List<Linje> = listOf(Linje(fom, tom, 1000, 100.0))) = """
    {
        "fødselsnummer": "$fødselsnummer",
        "aktørId": "1427484794278",
        "organisasjonsnummer": "$orgnummer",
        "fagsystemId": "XPJPZQYJ45DVHBSSQPMXTE2OP4",
        "utbetalingslinjer": ${objectMapper.writeValueAsString(utbetalingslinjer)},
        "annullertAvSaksbehandler": "$tidsstempel",
        "saksbehandlerEpost": "ASDASD",
        "system_read_count": 0,
        "system_participating_services": [
            {
                "service": "spleis",
                "instance": "spleis-55b67f658c-pq9tw",
                "time": "2020-10-30T11:12:05.583425"
            }
        ],
        "@event_name": "utbetaling_annullert",
        "@id": "4778a52b-dcbc-4bd2-bf42-e693dab3178f",
        "@opprettet": "2020-10-30T11:12:05.5835",
        "@forårsaket_av": {
            "event_name": "behov",
            "id": "4a50b680-3b05-438b-993b-57a6e58828cd",
            "opprettet": "2020-10-30T11:12:05.179565"
        }
    }
    """
}

data class Linje(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int,
    val grad: Double
)
