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
    fun `lagrer sykmelding`() {
        val nySøknadHendelseId = UUID.randomUUID()
        val sykmeldingId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))

        val dokumenter = dokumentDao.finn(listOf(nySøknadHendelseId))

        assertEquals(2, dokumenter.size)
    }

//    @Test
//    fun `skriver dokumenter til hendelse`() {
//        val søknadHendelseId = UUID.randomUUID()
//        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
//        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
//        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
//
//        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
//        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))
//
//        val dokumenter = dokumentDao.finn(listOf(sykmelding.hendelseId, søknad.hendelseId, inntektsmelding.hendelseId))
//        assertEquals(Dokumenter(sykmelding, søknad, inntektsmelding), dokumenter)
//    }
//
//    @Test
//    fun `håndterer duplikate dokumenter`() {
//        val søknadHendelseId = UUID.randomUUID()
//        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
//        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
//        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)
//
//        testRapid.sendTestMessage(sendtSøknadArbeidsgiverMessage(sykmelding, søknad))
//        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
//        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))
//
//        val dokumenter = dokumentDao.finn(listOf(sykmelding.hendelseId, søknad.hendelseId, inntektsmelding.hendelseId))
//        assertEquals(Dokumenter(sykmelding, søknad, inntektsmelding), dokumenter)
//    }

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

//    private fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
//        """{
//            "@event_name": "sendt_søknad_nav",
//            "@id": "${sykmelding.hendelseId}",
//            "id": "${søknad.dokumentId}",
//            "sykmeldingId": "${sykmelding.dokumentId}"
//        }"""
//
//    private fun sendtSøknadArbeidsgiverMessage(sykmelding: Hendelse, søknad: Hendelse) =
//        """{
//            "@event_name": "sendt_søknad_arbeidsgiver",
//            "@id": "${sykmelding.hendelseId}",
//            "id": "${søknad.dokumentId}",
//            "sykmeldingId": "${sykmelding.dokumentId}"
//        }"""
//
//    private fun inntektsmeldingMessage(hendelse: Hendelse) =
//        """{
//            "@event_name": "inntektsmelding",
//            "@id": "${hendelse.hendelseId}",
//            "inntektsmeldingId": "${hendelse.dokumentId}"
//        }"""
}
