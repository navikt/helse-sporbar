package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.asOffsetDateTime
import no.nav.helse.sporbar.tilSøknader
import org.slf4j.LoggerFactory
import java.util.*

internal class BehandlingOpprettetRiver(rapid: RapidsConnection, private val spedisjonClient: SpedisjonClient, private val sisPublisher: SisPublisher) :
    River.PacketListener {

    init {
        River(rapid).apply {
            precondition { it.requireValue("@event_name", "behandling_opprettet") }
            validate {
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
        val callId = UUID.randomUUID().toString()
        withMDC(mapOf(
            "vedtaksperiodeId" to "$vedtaksperiodeId",
            "behandlingId" to "$behandlingId",
            "callId" to callId,
        )) {
            behandlingOpprettet(packet, vedtaksperiodeId, behandlingId, interneSøknadIder, callId)
        }
    }

    private fun behandlingOpprettet(
        packet: JsonMessage,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
        interneSøknadIder: List<UUID>,
        callId: String
    ) {
        logg.info("Henter dokumenter for $interneSøknadIder")
        sikkerlogg.info("Henter dokumenter for $interneSøknadIder")
        val eksterneSøknadIder = retryBlocking {
            spedisjonClient.hentMeldinger(interneSøknadIder, callId).getOrThrow().tilSøknader()
        } ?: emptySet()
        if (eksterneSøknadIder.isEmpty()) sikkerlogg.info("Nå kom det en behandling_opprettet uten at vi fant eksterne søknadIder. Det er jo ikke rart.")
        val tidspunkt = packet["@opprettet"].asOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingOpprettet(vedtaksperiodeId, behandlingId, tidspunkt, eksterneSøknadIder))
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingOpprettetRiver::class.java)
    }
}
