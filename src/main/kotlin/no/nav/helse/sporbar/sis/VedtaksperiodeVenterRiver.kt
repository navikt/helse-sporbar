package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.toUUID
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

internal class VedtaksperiodeVenterRiver(rapid: RapidsConnection, private val sisPublisher: SisPublisher) :
    River.PacketListener {

    init {
        River(rapid).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_venter")
                it.demandValue("venterPå.venteårsak.hva", "GODKJENNING")
                it.requireKey("vedtaksperiodeId", "behandlingId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke vedtaksperiode_venter pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke vedtaksperiode_venter pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val behandlingId = packet["behandlingId"].asText().toUUID()
        val tidspunkt = packet["@opprettet"].asLocalDateTime().atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, lagBehandlingStatus(vedtaksperiodeId, behandlingId, tidspunkt, "VENTER_PÅ_SAKSBEHANDLER"))
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(VedtaksperiodeVenterRiver::class.java)
    }
}
