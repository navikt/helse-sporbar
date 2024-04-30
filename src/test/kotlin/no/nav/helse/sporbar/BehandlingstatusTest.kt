package no.nav.helse.sporbar

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.sis.BehandlingForkastetRiver
import no.nav.helse.sporbar.sis.BehandlingLukketRiver
import no.nav.helse.sporbar.sis.BehandlingOpprettetRiver
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
        sendSøknad(søknadId)
        sendBehandlingOpprettet(søknadId)
        sendVedtaksperiodeVenter("GODKJENNING")
        sendBehandlingLukket()

        assertEquals(4, sisPublisher.sendteMeldinger.size)
        assertEquals(listOf("OPPRETTET", "VENTER_PÅ_ARBEIDSGIVER", "VENTER_PÅ_SAKSBEHANDLER", "FERDIG"), sisPublisher.sendteStatuser)
    }

    @Test
    fun `Behandles utenfor Speil`() {
        val søknadId = UUID.randomUUID()
        sendSøknad(søknadId)
        sendBehandlingOpprettet(søknadId)
        sendBehandlingForkastet()

        assertEquals(3, sisPublisher.sendteMeldinger.size)
        assertEquals(listOf("OPPRETTET", "VENTER_PÅ_ARBEIDSGIVER", "BEHANDLES_UTENFOR_SPEIL"), sisPublisher.sendteStatuser)
    }

    private fun sendSøknad(søknadId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "sendt_søknad_nav",
          "@id": "$søknadId",
          "id": "${UUID.randomUUID()}",
          "sykmeldingId": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun sendBehandlingOpprettet(søknadId: UUID) {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_opprettet",
          "@id": "${UUID.randomUUID()}",
          "kilde": {
            "meldingsreferanseId": "$søknadId"
          },
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "${UUID.randomUUID()}",
          "behandlingId": "${UUID.randomUUID()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun sendVedtaksperiodeVenter(hva: String) {
        @Language("JSON")
        val melding = """{
          "@event_name": "vedtaksperiode_venter",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "${UUID.randomUUID()}",
          "behandlingId": "${UUID.randomUUID()}",
          "venterPå": {
            "venteårsak": {
              "hva": "$hva"
            }
          }
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }
    private fun sendBehandlingLukket() {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_lukket",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "${UUID.randomUUID()}",
          "behandlingId": "${UUID.randomUUID()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }

    private fun sendBehandlingForkastet() {
        @Language("JSON")
        val melding = """{
          "@event_name": "behandling_forkastet",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "${LocalDateTime.now()}",
          "vedtaksperiodeId": "${UUID.randomUUID()}",
          "behandlingId": "${UUID.randomUUID()}"
        }""".trimIndent()
        testRapid.sendTestMessage(melding)
    }

    private class TestSisPublisher: SisPublisher {
        val sendteMeldinger = mutableListOf<String>()
        val sendteStatuser = mutableListOf<String>()
        override fun send(vedtaksperiodeId: UUID, melding: String) {
            sendteMeldinger.add(melding)
            sendteStatuser.add(mapper.readTree(melding).path("status").asText())
        }
        private companion object {
            private val mapper = jacksonObjectMapper()
        }
    }

}