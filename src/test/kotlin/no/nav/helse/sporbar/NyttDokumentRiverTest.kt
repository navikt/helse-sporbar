package no.nav.helse.sporbar

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NyttDokumentRiverTest {
    private val postgres = PostgreSQLContainer<Nothing>("postgres:13").also { it.start() }
    private val dataSource = postgres.dataSource()
    private val testRapid = TestRapid()
    private val dokumentDao = DokumentDao(dataSource)

    init {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
        NyttDokumentRiver(testRapid, dokumentDao)
    }

    @AfterAll
    fun cleanUp() {
        dataSource.close()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `ny søknad hendelse`() {
        val nySøknadHendelseId = UUID.randomUUID()
        val sykmeldingId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))

        val dokumenter = dokumentDao.finn(listOf(nySøknadHendelseId))

        assertEquals(2, dokumenter.size)
    }

    @Test
    fun `duplikate hendelser for ny søknad hendelse og en sendt søknad hendelse`() {
        val nySøknadHendelseId = UUID.randomUUID()
        val sendtSøknadHendelseId = UUID.randomUUID()
        val sykmeldingId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))
        testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))
        testRapid.sendTestMessage(sendtSøknadMessage(sendtSøknadHendelseId, sykmeldingId, søknadId))

        val dokumenterForNySøknad = dokumentDao.finn(listOf(nySøknadHendelseId))
        assertEquals(2, dokumenterForNySøknad.size)
        val dokumenterForSendtSøknad = dokumentDao.finn(listOf(sendtSøknadHendelseId))
        assertEquals(2, dokumenterForSendtSøknad.size)
        assertEquals(dokumenterForNySøknad.map { it.dokumentId }, dokumenterForSendtSøknad.map { it.dokumentId })
    }

    @Test
    fun `ny søknad hendelse med hendelseId som ikke er gyldig UUID-format`() {
        val nySøknadHendelseId = "1234-1234-1234-1234"
        val sykmeldingId = UUID.randomUUID().toString()
        val søknadId = UUID.randomUUID().toString()
        assertDoesNotThrow {
            testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))
        }
    }

    @Test
    fun `ny søknad hendelse med sykmeldingId som ikke er gyldig UUID-format`() {
        val nySøknadHendelseId = UUID.randomUUID().toString()
        val sykmeldingId = "3456-3456-3456-3456"
        val søknadId = UUID.randomUUID().toString()
        assertDoesNotThrow {
            testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))
        }
    }

    @Test
    fun `ny søknad hendelse med søknadId som ikke er gyldig UUID-format`() {
        val nySøknadHendelseId = UUID.randomUUID().toString()
        val sykmeldingId = UUID.randomUUID().toString()
        val søknadId = "5678-5678-5678-5678"
        assertDoesNotThrow {
            testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))
        }
    }

    @Language("JSON")
    private fun nySøknadMessage(
        nySøknadHendelseId: UUID,
        sykmeldingDokumentId: UUID,
        søknadDokumentId: UUID
    ) = nySøknadMessage(nySøknadHendelseId.toString(), sykmeldingDokumentId.toString(), søknadDokumentId.toString())

    @Language("JSON")
    private fun nySøknadMessage(
        nySøknadHendelseId: String,
        sykmeldingDokumentId: String,
        søknadDokumentId: String
    ) =
        """{
            "@event_name": "ny_søknad",
            "@id": "$nySøknadHendelseId",
            "id": "$søknadDokumentId",
            "sykmeldingId": "$sykmeldingDokumentId",
            "@opprettet": "2020-06-10T10:46:46.007854"
        }"""

    @Language("JSON")
    private fun sendtSøknadMessage(
        nySøknadHendelseId: UUID,
        sykmeldingDokumentId: UUID,
        søknadDokumentId: UUID
    ) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "$nySøknadHendelseId",
            "id": "$søknadDokumentId",
            "sykmeldingId": "$sykmeldingDokumentId",
            "@opprettet": "2020-06-11T10:46:46.007854"
        }"""
}
