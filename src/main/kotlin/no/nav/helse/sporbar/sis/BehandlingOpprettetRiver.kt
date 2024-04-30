package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helse.sporbar.DokumentDao
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

interface SisPublisher {
    fun send(vedtaksperiodeId: UUID, melding: String)
}
internal class BehandlingOpprettetRiver(rapid: RapidsConnection, private val dokumentDao: DokumentDao, private val sisPublisher: SisPublisher) :
    River.PacketListener {

    init {
        River(rapid).apply {
            validate {
                it.demandValue("@event_name", "behandling_opprettet")
                it.requireKey("vedtaksperiodeId", "behandlingId", "kilde.meldingsreferanseId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke behandling_opprettet pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_opprettet pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val behandlingId = packet["behandlingId"].asText().toUUID()
        val internSøknadId = packet["kilde.meldingsreferanseId"].asText().toUUID()
        val søknad = dokumentDao.finn(listOf(internSøknadId)).firstOrNull() ?: return
        val søknadId = søknad.dokumentId
        val tidspunkt = packet["@opprettet"].asLocalDateTime().atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, lagBehandlingOpprettet(vedtaksperiodeId, behandlingId, tidspunkt, søknadId))
        sisPublisher.send(vedtaksperiodeId, lagBehandlingStatus(vedtaksperiodeId, behandlingId, tidspunkt, "VENTER_PÅ_ARBEIDSGIVER"))
    }
    
    private fun lagBehandlingOpprettet(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, søknadId: UUID): String {
        @Language("JSON")
        val melding = """{
  "vedtaksperiodeId": "$vedtaksperiodeId",
  "behandlingId": "$behandlingId",
  "tidspunkt": "$tidspunkt",
  "status": "OPPRETTET",
  "eksternSøknadId": "$søknadId"
}"""
        return melding
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingOpprettetRiver::class.java)
    }
}

fun lagBehandlingStatus(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, status: String): String {
    @Language("JSON")
    val melding = """{
  "vedtaksperiodeId": "$vedtaksperiodeId",
  "behandlingId": "$behandlingId",
  "tidspunkt": "$tidspunkt",
  "status": "$status"
}"""
    return melding
}
