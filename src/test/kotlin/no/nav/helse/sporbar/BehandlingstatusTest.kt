package no.nav.helse.sporbar

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.sis.BehandlingForkastetRiver
import no.nav.helse.sporbar.sis.BehandlingLukketRiver
import no.nav.helse.sporbar.sis.BehandlingOpprettetRiver
import no.nav.helse.sporbar.sis.Behandlingstatusmelding
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.BEHANDLES_UTENFOR_SPEIL
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.FERDIG
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.OPPRETTET
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER
import no.nav.helse.sporbar.sis.SisPublisher
import no.nav.helse.sporbar.sis.VedtaksperiodeVenterRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BehandlingstatusTest {

    private val testRapid = TestRapid()
    private val dokumentDao = DokumentDao { TestDatabase.dataSource }

    private val sisPublisher = TestSisPublisher()

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        BehandlingOpprettetRiver(testRapid, dokumentDao, sisPublisher)
        VedtaksperiodeVenterRiver(testRapid, sisPublisher)
        BehandlingLukketRiver(testRapid, sisPublisher)
        BehandlingForkastetRiver(testRapid, sisPublisher)
    }

    @BeforeEach
    fun reset() {
        testRapid.reset()
    }

    @Test
    fun `Ny behandling som avsluttes`() {
        val søknadId = UUID.randomUUID()
        val eksternSøknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        sendSøknad(søknadId, eksternSøknadId)
        sendBehandlingOpprettet(vedtaksperiodeId, søknadId)
        sendVedtaksperiodeVenter(vedtaksperiodeId, "GODKJENNING")
        sendBehandlingLukket(vedtaksperiodeId)

        assertEquals(setOf(eksternSøknadId), sisPublisher.eksterneSøknadIder(vedtaksperiodeId))
        assertEquals(4, sisPublisher.sendteMeldinger[vedtaksperiodeId]?.size)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_SAKSBEHANDLER, FERDIG), sisPublisher.sendteStatuser[vedtaksperiodeId])
    }

    @Test
    fun `Behandles utenfor Speil`() {
        val søknadId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        sendSøknad(søknadId)
        sendBehandlingOpprettet(vedtaksperiodeId, søknadId)
        sendBehandlingForkastet(vedtaksperiodeId)

        assertEquals(3, sisPublisher.sendteMeldinger[vedtaksperiodeId]?.size)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, BEHANDLES_UTENFOR_SPEIL), sisPublisher.sendteStatuser[vedtaksperiodeId])
    }

    @Test
    fun `Out of order søknad`() {
        val søknadIdMars = UUID.randomUUID()
        val eksternSøknadIdMars = UUID.randomUUID()
        val vedtaksperiodeIdMars = UUID.randomUUID()
        sendSøknad(søknadIdMars, eksternSøknadIdMars)
        sendBehandlingOpprettet(vedtaksperiodeIdMars, søknadIdMars)
        sendVedtaksperiodeVenterPåGodkjenning(vedtaksperiodeIdMars)
        sendBehandlingLukket(vedtaksperiodeIdMars)
        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_SAKSBEHANDLER, FERDIG), sisPublisher.sendteStatuser[vedtaksperiodeIdMars])
        assertEquals(setOf(eksternSøknadIdMars), sisPublisher.eksterneSøknadIder(vedtaksperiodeIdMars))

        val søknadIdJanuar = UUID.randomUUID()
        val eksternSøknadIdJanuar = UUID.randomUUID()
        val vedtaksperiodeIdJanuar = UUID.randomUUID()

        sendSøknad(søknadIdJanuar, eksternSøknadIdJanuar)
        sendBehandlingOpprettet(vedtaksperiodeIdJanuar, søknadIdJanuar)
        sendBehandlingOpprettet(vedtaksperiodeIdMars, søknadIdJanuar) // Feil1: Ettersom januar-søknaden er den som sparker i gang showet, så peker alt på den

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER, VENTER_PÅ_SAKSBEHANDLER, FERDIG, OPPRETTET, VENTER_PÅ_ARBEIDSGIVER), sisPublisher.sendteStatuser[vedtaksperiodeIdMars])
        assertEquals(setOf(eksternSøknadIdJanuar, eksternSøknadIdMars), sisPublisher.eksterneSøknadIder(vedtaksperiodeIdMars)) // Feil1: Mars-perioden får kobling mot januarSøknad

        assertEquals(listOf(OPPRETTET, VENTER_PÅ_ARBEIDSGIVER), sisPublisher.sendteStatuser[vedtaksperiodeIdJanuar]) // Feil2: Vi trenger ikke noe mer fra arbeidsgiver her
        assertEquals(setOf(eksternSøknadIdJanuar), sisPublisher.eksterneSøknadIder(vedtaksperiodeIdJanuar))
    }

    private fun sendSøknad(søknadId: UUID, eksternSøknadId: UUID = UUID.randomUUID()) {
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
    private fun sendBehandlingOpprettet(vedtaksperiodeId: UUID, søknadId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_opprettet",
          "@id": "${UUID.randomUUID()}",
          "kilde": {
            "meldingsreferanseId": "$søknadId"
          },
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "behandlingId": "${UUID.randomUUID()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun sendVedtaksperiodeVenter(vedtaksperiodeId: UUID, hva: String) {
        @Language("JSON")
        val melding = """{
          "@event_name": "vedtaksperiode_venter",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "behandlingId": "${UUID.randomUUID()}",
          "venterPå": {
            "venteårsak": {
              "hva": "$hva"
            }
          }
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun sendVedtaksperiodeVenterPåGodkjenning(vedtaksperiodeId: UUID) = sendVedtaksperiodeVenter(vedtaksperiodeId, "GODKJENNING")
    private fun sendBehandlingLukket(vedtaksperiodeId: UUID) {
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

    private fun sendBehandlingForkastet(vedtaksperiodeId: UUID) {
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
        val sendteMeldinger = mutableMapOf<UUID, MutableList<Behandlingstatusmelding>>()
        val sendteStatuser = mutableMapOf<UUID, MutableList<Behandlingstatusmelding.Behandlingstatustype>>()
        override fun send(vedtaksperiodeId: UUID, melding: Behandlingstatusmelding) {
            sendteMeldinger.compute(vedtaksperiodeId) { _, forrige ->
                forrige?.plus(melding)?.toMutableList() ?: mutableListOf(melding)
            }
            sendteStatuser.compute(vedtaksperiodeId) { _, forrige ->
                forrige?.plus(melding.status)?.toMutableList() ?: mutableListOf(melding.status)
            }
        }

        fun eksterneSøknadIder(vedtaksperiodeId: UUID) = sendteMeldinger[vedtaksperiodeId]?.mapNotNull { it.eksternSøknadId }?.toSet() ?: emptySet()
    }

}