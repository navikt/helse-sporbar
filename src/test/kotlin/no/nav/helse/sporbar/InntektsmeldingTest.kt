package no.nav.helse.sporbar

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.inntektsmelding.TrengerIkkeInntektsmelding
import no.nav.helse.sporbar.inntektsmelding.TrengerInntektsmelding
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InntektsmeldingTest {

    private val testRapid = TestRapid()
    private val fnr = "12345678910"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()

    private var trengerInntektsmeldingRiver = TrengerInntektsmelding(testRapid)
    private var trengerIkkeInntektsmeldingRiver = TrengerIkkeInntektsmelding(testRapid)

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `Kan lese trenger_inntektsmelding og trenger_ikke_inntektsmelding`() {
        assertFalse(trengerInntektsmeldingRiver.lestEnMelding)
        assertFalse(trengerIkkeInntektsmeldingRiver.lestEnMelding)

        testRapid.sendTestMessage(eventSomJson("trenger_inntektsmelding"))
        testRapid.sendTestMessage(eventSomJson("trenger_ikke_inntektsmelding"))

        assertTrue(trengerInntektsmeldingRiver.lestEnMelding)
        assertTrue(trengerIkkeInntektsmeldingRiver.lestEnMelding)
    }


    private fun eventSomJson(type: String = "trenger_inntektsmelding"): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to type,
                "vedtaksperiodeId" to UUID.randomUUID(),
                "fødselsnummer" to fnr,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to LocalDate.now(),
                "tom" to LocalDate.now().plusDays(10),
                "søknadIder" to setOf(UUID.randomUUID())
            )
        ).toJson()
    }
}
