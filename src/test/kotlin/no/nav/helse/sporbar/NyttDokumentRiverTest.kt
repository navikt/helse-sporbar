package no.nav.helse.sporbar

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class NyttDokumentRiverTest {
    private val testRapid = TestRapid()
    private val embeddedPostgres = EmbeddedPostgres.builder().setPort(56789).start()
    private val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
    private val dataSource = HikariDataSource(hikariConfig)
    private val dokumentDao = DokumentDao(dataSource)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
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

    private fun nySøknadMessage(
        nySøknadHendelseId: UUID,
        sykmeldingDokumentId: UUID,
        søknadDokumentId: UUID
    ) =
        """{
            "@event_name": "ny_søknad",
            "@id": "$nySøknadHendelseId",
            "id": "$søknadDokumentId",
            "sykmeldingId": "$sykmeldingDokumentId",
            "@opprettet": "2020-06-10T10:46:46.007854"
        }"""

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
