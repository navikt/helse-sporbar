package no.nav.helse.sporbar

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VedtaksperiodeEndretRiverTest {
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
    private val vedtaksperiodeDao = VedtaksperiodeDao(dataSource)
    val fnr = "12020052345"

    init {
        VedtaksperiodeEndretRiver(testRapid, vedtaksperiodeDao)

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
    fun test() {
        testRapid.sendTestMessage(vedtaksperiodeEndret(fnr))
        assertEquals(1, vedtaksperiodeDao.finn(fnr).size)
    }
}

@Language("JSON")
private fun vedtaksperiodeEndret(fnr: String) = """{
    "vedtaksperiodeId": "e133b521-ffe2-4ad0-bfe8-9e29e9583381",
    "organisasjonsnummer": "987654321",
    "gjeldendeTilstand": "MOTTATT_SYKMELDING_FERDIG_GAP",
    "forrigeTilstand": "START",
    "aktivitetslogg": {
        "aktiviteter": []
    },
    "vedtaksperiode_aktivitetslogg": {
        "aktiviteter": [],
        "kontekster": []
    },
    "hendelser": [
        "75be4efa-fa13-44a9-afc2-6583dd87d626"
    ],
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
    "fødselsnummer": "$fnr"
}
"""
