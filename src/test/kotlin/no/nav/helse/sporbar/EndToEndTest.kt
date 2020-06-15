package no.nav.helse.sporbar

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*
import kotlin.streams.asSequence

private const val FNR = "12020052345"
private const val ORGNUMMER = "987654321"

internal class EndToEndTest {
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
    private val vedtaksperiodeDao = VedtaksperiodeDao(dataSource)
    private val vedtakDao = VedtakDao(dataSource)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        VedtaksperiodeEndretRiver(testRapid, vedtaksperiodeDao)
        UtbetaltRiver(testRapid, vedtakDao)

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
        val sendtSøknadHendelseId = UUID.randomUUID()
        val sykmeldingId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        testRapid.sendTestMessage(nySøknadMessage(nySøknadHendelseId, sykmeldingId, søknadId))
        testRapid.sendTestMessage(vedtaksperiodeEndret(vedtaksperiodeId, "START", "MOTTATT_SYKMELDING_FERDIG_GAP", listOf(nySøknadHendelseId)))

        val vedtaksperiodeEtterSykmelding = vedtaksperiodeDao.finn(FNR).first()
        assertEquals(Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP, vedtaksperiodeEtterSykmelding.tilstand)

        testRapid.sendTestMessage(sendtSøknadMessage(sendtSøknadHendelseId, sykmeldingId, søknadId))
        testRapid.sendTestMessage(vedtaksperiodeEndret(vedtaksperiodeId, "MOTTATT_SYKMELDING_FERDIG_GAP", "AVVENTER_GAP", listOf(nySøknadHendelseId, sendtSøknadHendelseId)))

        val vedtaksperiodeEtterSøknad = vedtaksperiodeDao.finn(FNR).first()
        assertEquals(Vedtaksperiode.Tilstand.AVVENTER_GAP, vedtaksperiodeEtterSøknad.tilstand)

        assertEquals(2, vedtaksperiodeEtterSøknad.dokumenter.size)
        assertEquals(sykmeldingId, vedtaksperiodeEtterSøknad.dokumenter.first { it.type == Dokument.Type.Sykmelding }.dokumentId)
        assertEquals(søknadId, vedtaksperiodeEtterSøknad.dokumenter.first { it.type == Dokument.Type.Søknad }.dokumentId)

        val utbetalinghendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(utbetalingMessage(utbetalinghendelseId, listOf(nySøknadHendelseId, sendtSøknadHendelseId)))

        val vedtaksperiodeEtterUtbetaling = vedtaksperiodeDao.finn(FNR).first()
        assertNotNull(vedtaksperiodeEtterUtbetaling.vedtak)

        assertEquals(2, vedtaksperiodeEtterUtbetaling.vedtak?.oppdrag?.size)
    }

    @Language("JSON")
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

    @Language("JSON")
    private fun vedtaksperiodeEndret(vedtaksperiodeId: UUID, forrige: String, gjeldendeTilstand: String, hendelser: List<UUID>) = """{
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "organisasjonsnummer": "$ORGNUMMER",
    "gjeldendeTilstand": "$gjeldendeTilstand",
    "forrigeTilstand": "$forrige",
    "aktivitetslogg": {
        "aktiviteter": []
    },
    "vedtaksperiode_aktivitetslogg": {
        "aktiviteter": [],
        "kontekster": []
    },
    "hendelser": ${hendelser.map { "\"${it}\"" }},
    "makstid": "2020-07-12T09:20:32.262525",
    "system_read_count": 0,
    "@event_name": "vedtaksperiode_endret",
    "@id": "9154ce4d-cb8a-4dc4-96e1-379c91f76d02",
    "@opprettet": "2020-06-12T09:20:56.552561",
    "@forårsaket_av": {
        "event_name": "ny_søknad",
        "id": "75be4efa-fa13-44a9-afc2-6583dd87d626",
        "opprettet": "2020-06-12T09:20:31.985479"
    },
    "aktørId": "42",
    "fødselsnummer": "$FNR"
}
"""

    @Language("JSON")
    private fun utbetalingMessage(
        hendelseId: UUID,
        hendelser: List<UUID>,
        fom: LocalDate = LocalDate.of(2020, 6, 1),
        tom: LocalDate = LocalDate.of(2020, 6, 10),
        tidligereBrukteSykedager: Int = 0
    ) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "$FNR",
    "organisasjonsnummer": "$ORGNUMMER",
    "hendelser": ${hendelser.map { "\"${it}\"" }},
    "utbetalt": [
        {
            "mottaker": "$ORGNUMMER",
            "fagområde": "SPREF",
            "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "$fom",
                    "tom": "$tom",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": ${sykedager(fom, tom)}
                }
            ]
        },
        {
            "mottaker": "$FNR",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "$fom",
    "tom": "$tom",
    "forbrukteSykedager": ${tidligereBrukteSykedager + sykedager(fom, tom)},
    "gjenståendeSykedager": ${248 - tidligereBrukteSykedager - sykedager(fom, tom)},
    "opprettet": "2020-05-04T11:26:30.23846",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "$hendelseId",
    "@opprettet": "2020-05-04T11:27:13.521398",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()
}
