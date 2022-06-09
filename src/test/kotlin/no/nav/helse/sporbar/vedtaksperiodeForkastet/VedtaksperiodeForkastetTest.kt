package no.nav.helse.sporbar.vedtaksperiodeForkastet

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.TestDatabase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VedtaksperiodeForkastetTest {

    private val testRapid = TestRapid()
    private val fnr = "12345678910"
    private val aktørId = "1427484794278"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()
    private val vedtaksperiodeId = UUID.randomUUID()

    private val vedtaksperiodeForkastetDao = VedtaksperiodeForkastetDao(TestDatabase.dataSource)

    init {
        VedtaksperiodeForkastet(testRapid, vedtaksperiodeForkastetDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `Kan lese vedtaksperiode_forkastet`() {
        assertFalse(finn(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeForkastet(vedtaksperiodeId))
        assertTrue(finn(vedtaksperiodeId))
    }

    private fun vedtaksperiodeForkastet(vedtaksperiodeId: UUID): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to "vedtaksperiode_forkastet",
                "@id" to UUID.randomUUID(),
                "vedtaksperiodeId" to vedtaksperiodeId,
                "fødselsnummer" to fnr,
                "aktørId" to aktørId,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to LocalDate.now(),
                "tom" to LocalDate.now().plusDays(10),
                "hendelser" to setOf(UUID.randomUUID())
            )
        ).toJson()
    }

    private fun finn(vedtaksperiodeId: UUID): Boolean {
        return sessionOf(TestDatabase.dataSource).use { session ->
            session.run(queryOf("SELECT COUNT(1) FROM vedtaksperiode_forkastet WHERE vedtaksperiode_id = ?", vedtaksperiodeId).map { it.int(1) > 0 }.asSingle)
        } ?: false
    }
}
