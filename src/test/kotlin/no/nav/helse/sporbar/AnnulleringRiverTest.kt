package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.result_object.ok
import com.github.navikt.tbd_libs.speed.IdentResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson

class AnnulleringRiverTest {

    companion object {
        val fødselsnummer = "12345678910"
        val organisasjonsnummer = "123456789"
        val tidsstempel = LocalDateTime.now()
        val fom = LocalDate.now()
        val tom = LocalDate.now()
        val utbetalingId = UUID.randomUUID()
        val korrelasjonsId = UUID.randomUUID()
        val personFagsystemId = "FagsystemIdPerson"
        val arbeidsgiverFagsystemId = "FagsystemIdArbeidsgiver"
    }

    private val testRapid = TestRapid()
    private val aivenProducerMock = mockk<KafkaProducer<String,String>>(relaxed = true)
    private val speedClient = mockk<SpeedClient> {
        every { hentFødselsnummerOgAktørId(any(), any()) } returns IdentResponse(
            fødselsnummer = fødselsnummer,
            aktørId = "aktørId",
            npid = null,
            kilde = IdentResponse.KildeResponse.PDL
        ).ok()
    }
    init {
        AnnulleringRiver(testRapid, aivenProducerMock, speedClient)
    }

    @Test
    fun `vanlig annullering`() {
        testRapid.sendTestMessage(annullering(
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            personFagsystemId = personFagsystemId
        ))

        val captureSlot = CapturingSlot<ProducerRecord<String, String>>()
        verify { aivenProducerMock.send( capture(captureSlot) ) }

        val annullering = captureSlot.captured
        assertEquals(fødselsnummer, annullering.key())

        val annulleringJson = annullering.validertJson()
        assertEquals(fødselsnummer, annulleringJson["fødselsnummer"].textValue())
        assertEquals(organisasjonsnummer, annulleringJson["organisasjonsnummer"].textValue())
        assertEquals(organisasjonsnummer, annulleringJson["orgnummer"].textValue())
        assertEquals(tidsstempel, annulleringJson["tidsstempel"].asLocalDateTime())
        assertEquals(fom, annulleringJson["fom"].asLocalDate())
        assertEquals(tom, annulleringJson["tom"].asLocalDate())
        assertEquals(personFagsystemId, annulleringJson["personFagsystemId"].asText())
        assertEquals(arbeidsgiverFagsystemId, annulleringJson["arbeidsgiverFagsystemId"].asText())
        assertEquals("utbetaling_annullert", annulleringJson["event"].asText())
    }

    @Test
    fun `annullering full refusjon`() {
        testRapid.sendTestMessage(annullering(
            personFagsystemId = null,
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId
        ))

        val captureSlot = CapturingSlot<ProducerRecord<String, String>>()
        verify { aivenProducerMock.send( capture(captureSlot) ) }

        val annullering = captureSlot.captured
        assertEquals(fødselsnummer, annullering.key())

        val annulleringJson = annullering.validertJson()
        assertEquals(fødselsnummer, annulleringJson["fødselsnummer"].textValue())
        assertEquals(organisasjonsnummer, annulleringJson["organisasjonsnummer"].textValue())
        assertEquals(organisasjonsnummer, annulleringJson["orgnummer"].textValue())
        assertEquals(tidsstempel, annulleringJson["tidsstempel"].asLocalDateTime())
        assertEquals(fom, annulleringJson["fom"].asLocalDate())
        assertEquals(tom, annulleringJson["tom"].asLocalDate())
        assertTrue(annulleringJson["personFagsystemId"].isMissingOrNull())
        assertEquals(arbeidsgiverFagsystemId, annulleringJson["arbeidsgiverFagsystemId"].asText())
        assertEquals("utbetaling_annullert", annulleringJson["event"].asText())
    }

    @Test
    fun `annullering ingen refusjon`() {
        testRapid.sendTestMessage(annullering(
            personFagsystemId = personFagsystemId,
            arbeidsgiverFagsystemId = null
        ))

        val captureSlot = CapturingSlot<ProducerRecord<String, String>>()
        verify { aivenProducerMock.send( capture(captureSlot) ) }

        val annullering = captureSlot.captured
        assertEquals(fødselsnummer, annullering.key())

        val annulleringJson = annullering.validertJson()
        assertEquals(fødselsnummer, annulleringJson["fødselsnummer"].textValue())
        assertEquals(organisasjonsnummer, annulleringJson["organisasjonsnummer"].textValue())
        assertEquals(organisasjonsnummer, annulleringJson["orgnummer"].textValue())
        assertEquals(tidsstempel, annulleringJson["tidsstempel"].asLocalDateTime())
        assertEquals(fom, annulleringJson["fom"].asLocalDate())
        assertEquals(tom, annulleringJson["tom"].asLocalDate())
        assertEquals(personFagsystemId, annulleringJson["personFagsystemId"].asText())
        assertTrue(annulleringJson["arbeidsgiverFagsystemId"].isMissingOrNull())
        assertEquals("utbetaling_annullert", annulleringJson["event"].asText())
    }

    @Language("JSON")
    private fun annullering(
        personFagsystemId: String?,
        arbeidsgiverFagsystemId: String?
    ) = """
    {
        "fødselsnummer": "$fødselsnummer",
        "organisasjonsnummer": "$organisasjonsnummer",
        "tidspunkt": "$tidsstempel",
        "fom": "$fom",
        "tom": "$tom",
        "@event_name": "utbetaling_annullert",
        "@id": "4778a52b-dcbc-4bd2-bf42-e693dab3178f",
        "@opprettet": "2020-10-30T11:12:05.5835",
        "utbetalingId": "$utbetalingId",
        "korrelasjonsId": "$korrelasjonsId",
        "personFagsystemId": ${personFagsystemId?.let { "\"$it\"" }},
        "arbeidsgiverFagsystemId": ${arbeidsgiverFagsystemId?.let { "\"$it\"" }}
    }
    """
}
