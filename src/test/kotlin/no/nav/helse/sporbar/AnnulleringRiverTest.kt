package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnnulleringRiverTest {

    companion object {
        val fødselsnummer = "12345678910"
    }

    private val testRapid = TestRapid()
    private val producerMock = mockk<KafkaProducer<String,JsonNode>>(relaxed = true)
    init {
        AnnulleringRiver(testRapid, producerMock)
    }

    @Test
    fun test() {
        testRapid.sendTestMessage(annullering())

        val captureSlot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producerMock.send( capture(captureSlot) ) }

        val annullering = captureSlot.captured

        assertEquals(fødselsnummer, annullering.key())
    }

    private fun annullering() = """
        {
        "fødselsnummer": $fødselsnummer",
        "aktørId": "1427484794278",
        "organisasjonsnummer": "910825526",
        "fagsystemId": "XPJPZQYJ45DVHBSSQPMXTE2OP4",
        "utbetalingslinjer": [
        {
            "fom": "2020-07-17",
            "tom": "2020-07-31",
            "beløp": 11550,
            "grad": 100.0
        }
        ],
        "annullertAvSaksbehandler": "2020-10-30T11:12:05.062899",
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
    }"""

    private fun utgående() = """
        {
          "event_name": "Annullering",
          "fødselsnummer": "123123123123",
          "orgnummer": "891238912",
          "timestamp": "2020-10-30T11:12:05.062899",
          "utbetalingslinjer": [
            {
              "fom": "2020-07-17",
              "tom": "2020-07-31"
            }
          ]
        }
    """
}
