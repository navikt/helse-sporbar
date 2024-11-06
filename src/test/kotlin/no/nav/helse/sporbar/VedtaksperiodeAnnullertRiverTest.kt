package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson

class VedtaksperiodeAnnullertRiverTest {

    companion object {
        const val fødselsnummer = "12345678910"
        const val aktørId = "0000123456789"
        const val organisasjonsnummer = "123456789"
        val fom = LocalDate.now()
        val tom = LocalDate.now()
    }

    private val testRapid = TestRapid()
    private val aivenProducerMock = mockk<KafkaProducer<String,String>>(relaxed = true)

    init {
        VedtaksperiodeAnnullertRiver(testRapid, aivenProducerMock)
    }

    @Test
    fun `gyldig vedtaksperiode_annullert`() {
        testRapid.sendTestMessage(vedtaksperiodeAnnullert())

        val captureSlot = CapturingSlot<ProducerRecord<String, String>>()
        verify { aivenProducerMock.send( capture(captureSlot) ) }

        val vedtakAnnullert = captureSlot.captured
        assertEquals(fødselsnummer, vedtakAnnullert.key())

        val vedtakAnnullertJson = vedtakAnnullert.validertJson()
        assertEquals(fødselsnummer, vedtakAnnullertJson["fødselsnummer"].textValue())
        assertEquals(organisasjonsnummer, vedtakAnnullertJson["organisasjonsnummer"].textValue())
        assertEquals(fom, vedtakAnnullertJson["fom"].asLocalDate())
        assertEquals(tom, vedtakAnnullertJson["tom"].asLocalDate())
        assertEquals("vedtak_annullert", vedtakAnnullertJson["event"].asText())
    }
    @Language("JSON")
    private fun vedtaksperiodeAnnullert(
    ) = """
    {
        "fødselsnummer": "$fødselsnummer",
        "aktørId": "1427484794278",
        "organisasjonsnummer": "$organisasjonsnummer",
        "vedtaksperiodeId": "${UUID.randomUUID()}",
        "fom": "$fom",
        "tom": "$tom",
        "@event_name": "vedtaksperiode_annullert",
        "@id": "${UUID.randomUUID()}",
        "@opprettet": "2020-10-30T11:12:05.5835"
    }
    """
}
