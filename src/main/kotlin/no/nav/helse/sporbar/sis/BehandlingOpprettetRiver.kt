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
import no.nav.helse.sporbar.DokumentDao
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.asOffsetDateTime
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.eksterneSøknadIder
import org.slf4j.LoggerFactory

internal class BehandlingOpprettetRiver(rapid: RapidsConnection, private val dokumentDao: DokumentDao, private val sisPublisher: SisPublisher) :
    River.PacketListener {

    init {
        River(rapid).apply {
            validate {
                it.demandValue("@event_name", "behandling_opprettet")
                it.requireKey("vedtaksperiodeId", "behandlingId", "søknadIder")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.info("Håndterer ikke behandling_opprettet pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_opprettet pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val behandlingId = packet["behandlingId"].asText().toUUID()
        val interneSøknadIder = packet["søknadIder"].map { it.asText().toUUID() }
        val eksterneSøknadIder = dokumentDao.eksterneSøknadIder(interneSøknadIder) ?: return sikkerlogg.error("Nå kom det en behandling_opprettet uten at vi fant eksterne søknadIder. Er ikke dét rart?")
        val tidspunkt = packet["@opprettet"].asOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingOpprettet(vedtaksperiodeId, behandlingId, tidspunkt, eksterneSøknadIder))
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingstatus(vedtaksperiodeId, behandlingId, tidspunkt, VENTER_PÅ_ARBEIDSGIVER))
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingOpprettetRiver::class.java)
    }
}
