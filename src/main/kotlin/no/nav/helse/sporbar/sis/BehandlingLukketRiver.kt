package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.FERDIG
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.asOffsetDateTime
import org.slf4j.LoggerFactory

internal class BehandlingLukketRiver(rapid: RapidsConnection, private val sisPublisher: SisPublisher) :
    River.PacketListener {

    init {
        River(rapid).apply {
            precondition { it.requireValue("@event_name", "behandling_lukket") }
            validate {
                it.requireKey("vedtaksperiodeId", "behandlingId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.info("Håndterer ikke behandling_lukket pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_lukket pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val behandlingId = packet["behandlingId"].asText().toUUID()
        val tidspunkt = packet["@opprettet"].asOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingstatus(vedtaksperiodeId, behandlingId, tidspunkt, FERDIG))
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingLukketRiver::class.java)
    }
}
