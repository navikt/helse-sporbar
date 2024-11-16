package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sporbar.sis.*
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class BehandlingstatusTest {


    @Test
    fun `Ny behandling som avsluttes`() = e2e {
        val søknadId = UUID.randomUUID()
        val eksternSøknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        sendSøknad(søknadId, eksternSøknadId)
        sendBehandlingOpprettet(vedtaksperiodeId, søknadId)
        sendVedtaksperiodeVenterPåGodkjenning(vedtaksperiodeId, søknadId = søknadId)
        sendBehandlingLukket(vedtaksperiodeId)

        assertEquals(setOf(eksternSøknadId), sisPublisher.eksterneSøknadIder(vedtaksperiodeId))
        assertEquals(4, sisPublisher.sendteMeldinger(vedtaksperiodeId).size)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_SAKSBEHANDLER, FERDIG), sisPublisher.sendteStatuser(vedtaksperiodeId))
    }

    @Test
    fun `Behandles utenfor Speil`() = e2e {
        val søknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        sendSøknad(søknadId)
        sendBehandlingOpprettet(vedtaksperiodeId, søknadId)
        sendBehandlingForkastet(vedtaksperiodeId)

        assertEquals(3, sisPublisher.sendteMeldinger(vedtaksperiodeId).size)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, BEHANDLES_UTENFOR_SPEIL), sisPublisher.sendteStatuser(vedtaksperiodeId))
    }

    @Test
    fun `Out of order søknad`() = e2e {
        val søknadIdMars = UUID.randomUUID()
        val eksternSøknadIdMars = UUID.randomUUID()
        val vedtaksperiodeIdMars = UUID.randomUUID()
        sendSøknad(søknadIdMars, eksternSøknadIdMars)
        sendBehandlingOpprettet(vedtaksperiodeIdMars, søknadIdMars)
        sendVedtaksperiodeVenterPåGodkjenning(vedtaksperiodeIdMars, søknadId = søknadIdMars)
        sendBehandlingLukket(vedtaksperiodeIdMars)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_SAKSBEHANDLER, FERDIG), sisPublisher.sendteStatuser(vedtaksperiodeIdMars))
        assertEquals(setOf(eksternSøknadIdMars), sisPublisher.eksterneSøknadIder(vedtaksperiodeIdMars))

        val søknadIdJanuar = UUID.randomUUID()
        val eksternSøknadIdJanuar = UUID.randomUUID()
        val vedtaksperiodeIdJanuar = UUID.randomUUID()

        sendSøknad(søknadIdJanuar, eksternSøknadIdJanuar)
        sendBehandlingOpprettet(vedtaksperiodeIdJanuar, søknadIdJanuar)
        sendBehandlingOpprettet(vedtaksperiodeIdMars, søknadIdJanuar) // Feil1: Ettersom januar-søknaden er den som sparker i gang showet, så peker alt på den
        sendVedtaksperiodeVenter(vedtaksperiodeIdJanuar, "INNTEKTSMELDING", vedtaksperiodeIdJanuar, søknadId = søknadIdJanuar)
        sendVedtaksperiodeVenter(vedtaksperiodeIdMars, "INNTEKTSMELDING", vedtaksperiodeIdJanuar, søknadId = søknadIdMars)

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_SAKSBEHANDLER, FERDIG, OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_ANNEN_PERIODE), sisPublisher.sendteStatuser(vedtaksperiodeIdMars))
        assertEquals(setOf(eksternSøknadIdJanuar, eksternSøknadIdMars), sisPublisher.eksterneSøknadIder(vedtaksperiodeIdMars)) // Feil1: Mars-perioden får kobling mot januarSøknad

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER), sisPublisher.sendteStatuser(vedtaksperiodeIdJanuar))
        assertEquals(setOf(eksternSøknadIdJanuar), sisPublisher.eksterneSøknadIder(vedtaksperiodeIdJanuar))
    }

    @Test
    fun `Overlappende søknader fra to arbeidsgivere`() = e2e {
        val søknadIdAG1 = UUID.randomUUID()
        val eksternSøknadIdAG1 = UUID.randomUUID()
        val vedtaksperiodeIdAG1 = UUID.randomUUID()
        val søknadIdAG2 = UUID.randomUUID()
        val eksternSøknadIdAG2 = UUID.randomUUID()
        val vedtaksperiodeIdAG2 = UUID.randomUUID()
        sendSøknad(søknadIdAG1, eksternSøknadIdAG1)
        sendBehandlingOpprettet(vedtaksperiodeIdAG1, søknadIdAG1)
        sendSøknad(søknadIdAG2, eksternSøknadIdAG2)
        sendBehandlingOpprettet(vedtaksperiodeIdAG2, søknadIdAG2)

        // Arbeidsgiver 1 sender inn inntektsmelding
        sendVedtaksperiodeVenter(vedtaksperiodeIdAG1, "INNTEKTSMELDING", vedtaksperiodeIdAG2, venterPåOrganisasjonsnummer = "AG2", søknadId = søknadIdAG1)
        sendVedtaksperiodeVenter(vedtaksperiodeIdAG2, "INNTEKTSMELDING", vedtaksperiodeIdAG2, søknadId = søknadIdAG2)

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_ANNEN_PERIODE), sisPublisher.sendteStatuser(vedtaksperiodeIdAG1))
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER), sisPublisher.sendteStatuser(vedtaksperiodeIdAG2))
    }

    @Test
    fun `Venter på søknad på annen arbeidsgiver`() = e2e {
        val søknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendBehandlingOpprettet(vedtaksperiodeId, søknadId)
        sendVedtaksperiodeVenter(vedtaksperiodeId, "SØKNAD", vedtaksperiodeId, søknadId = søknadId)

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_ANNEN_PERIODE), sisPublisher.sendteStatuser(vedtaksperiodeId))
    }

    @Test
    fun `Venter selv på inntektsmelding skal ikke gi ny status`() = e2e {
        val søknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()

        sendSøknad(søknadId)
        sendBehandlingOpprettet(vedtaksperiodeId, søknadId)

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER), sisPublisher.sendteStatuser(vedtaksperiodeId))
        sendVedtaksperiodeVenter(vedtaksperiodeId, "INNTEKTSMELDING", vedtaksperiodeId, søknadId = søknadId)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER), sisPublisher.sendteStatuser(vedtaksperiodeId))
    }

    private data class E2ETestContext(
        val testRapid: TestRapid,
        val dokumentDao: DokumentDao,
        val sisPublisher: TestSisPublisher
    ) {
        init {
            NyttDokumentRiver(testRapid, dokumentDao)
            BehandlingOpprettetRiver(testRapid, dokumentDao, sisPublisher)
            VedtaksperiodeVenterRiver(testRapid, dokumentDao, sisPublisher)
            BehandlingLukketRiver(testRapid, sisPublisher)
            BehandlingForkastetRiver(testRapid, sisPublisher)
        }
    }
    private fun e2e(testblokk: E2ETestContext.() -> Unit) {
        val testDataSource = databaseContainer.nyTilkobling()
        try {
            val testRapid = TestRapid()
            val dokumentDao = DokumentDao(testDataSource::ds)
            val sisPublisher = TestSisPublisher()
            testblokk(E2ETestContext(testRapid, dokumentDao, sisPublisher))
        } finally {
            databaseContainer.droppTilkobling(testDataSource)
        }
    }

    private fun E2ETestContext.sendSøknad(søknadId: UUID, eksternSøknadId: UUID = UUID.randomUUID()) {
        @Language("JSON")
        val melding = """{
          "@event_name": "sendt_søknad_nav",
          "@id": "$søknadId",
          "id": "$eksternSøknadId",
          "sykmeldingId": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun E2ETestContext.sendBehandlingOpprettet(vedtaksperiodeId: UUID, søknadId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_opprettet",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "behandlingId": "${UUID.randomUUID()}",
          "søknadIder": ["$søknadId"]
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun E2ETestContext.sendVedtaksperiodeVenter(vedtaksperiodeId: UUID, hva: String, venterPåVedtaksperiodeId: UUID = vedtaksperiodeId, venterPåOrganisasjonsnummer: String = "999999999", søknadId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "vedtaksperiode_venter",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "hendelser": ["$søknadId"],
          "organisasjonsnummer": "999999999",
          "behandlingId": "${UUID.randomUUID()}",
          "venterPå": {
            "vedtaksperiodeId": "$venterPåVedtaksperiodeId",
            "organisasjonsnummer": "$venterPåOrganisasjonsnummer",
            "venteårsak": {
              "hva": "$hva"
            }
          }
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun E2ETestContext.sendVedtaksperiodeVenterPåGodkjenning(vedtaksperiodeId: UUID, søknadId: UUID) = sendVedtaksperiodeVenter(vedtaksperiodeId, "GODKJENNING", søknadId = søknadId)
    private fun E2ETestContext.sendBehandlingLukket(vedtaksperiodeId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_lukket",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "behandlingId": "${UUID.randomUUID()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun E2ETestContext.sendBehandlingForkastet(vedtaksperiodeId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_forkastet",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "behandlingId": "${UUID.randomUUID()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }

    private class TestSisPublisher: SisPublisher {
        private val sendteMeldinger = mutableMapOf<UUID, MutableList<Behandlingstatusmelding>>()
        private val sendteStatuser = mutableMapOf<UUID, MutableList<Behandlingstatusmelding.Behandlingstatustype>>()
        override fun send(vedtaksperiodeId: UUID, melding: Behandlingstatusmelding) {
            sendteMeldinger.compute(vedtaksperiodeId) { _, forrige ->
                forrige?.plus(melding)?.toMutableList() ?: mutableListOf(melding)
            }
            sendteStatuser.compute(vedtaksperiodeId) { _, forrige ->
                forrige?.plus(melding.status)?.toMutableList() ?: mutableListOf(melding.status)
            }
        }

        fun eksterneSøknadIder(vedtaksperiodeId: UUID) = sendteMeldinger[vedtaksperiodeId]?.flatMap { it.eksterneSøknadIder }?.toSet() ?: emptySet()
        fun sendteMeldinger(vedtaksperiodeId: UUID) = sendteMeldinger.getOrDefault(vedtaksperiodeId, mutableListOf()).toList()
        fun sendteStatuser(vedtaksperiodeId: UUID) = sendteStatuser.getOrDefault(vedtaksperiodeId, mutableListOf()).toList()
    }
}