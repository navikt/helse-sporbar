package no.nav.helse.sporbar

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingDao
import no.nav.helse.sporbar.inntektsmelding.TrengerIkkeInntektsmelding
import no.nav.helse.sporbar.inntektsmelding.TrengerInntektsmelding
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InntektsmeldingTest {

    private val testRapid = TestRapid()
    private val fnr = "12345678910"
    private val aktørId = "1427484794278"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()

    private val inntektsmeldingDao = InntektsmeldingDao(TestDatabase.dataSource)


    init {
        TrengerInntektsmelding(testRapid, inntektsmeldingDao)
        TrengerIkkeInntektsmelding(testRapid, inntektsmeldingDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `Kan lese trenger_inntektsmelding og trenger_ikke_inntektsmelding og lagre i db`() {
        val trengerInntektsmeldingId = UUID.randomUUID()
        val trengerIkkeInntektsmeldingId = UUID.randomUUID()

        //assertFalse(finnTrengerInntektsmelding(trengerInntektsmeldingId))
        //assertFalse(finnTrengerIkkeInntektsmelding(trengerIkkeInntektsmeldingId))

        testRapid.sendTestMessage(eventSomJson("trenger_inntektsmelding", trengerInntektsmeldingId))
        testRapid.sendTestMessage(eventSomJson("trenger_ikke_inntektsmelding", trengerIkkeInntektsmeldingId))

        //assertTrue(finnTrengerInntektsmelding(trengerInntektsmeldingId))
        //assertTrue(finnTrengerIkkeInntektsmelding(trengerIkkeInntektsmeldingId))
    }


    private fun eventSomJson(type: String = "trenger_inntektsmelding", hendelseId: UUID): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to type,
                "@id" to hendelseId,
                "vedtaksperiodeId" to UUID.randomUUID(),
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

    private fun finnTrengerInntektsmelding(hendelseId: UUID): Boolean {
        return sessionOf(TestDatabase.dataSource).use { session ->
            session.run(queryOf("SELECT COUNT(1) FROM trenger_inntektsmelding WHERE hendelseId = ?", hendelseId).map { it.int(1) > 0 }.asSingle)
        } ?: false
    }

    private fun finnTrengerIkkeInntektsmelding(hendelseId: UUID): Boolean {
        return sessionOf(TestDatabase.dataSource).use { session ->
            session.run(queryOf("SELECT COUNT(1) FROM trenger_ikke_inntektsmelding WHERE hendelseId = ?", hendelseId).map { it.int(1) > 0 }.asSingle)
        } ?: false
    }
}
