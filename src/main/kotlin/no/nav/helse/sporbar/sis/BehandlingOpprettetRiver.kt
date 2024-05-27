package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import java.time.ZoneId
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helse.sporbar.Dokument
import no.nav.helse.sporbar.DokumentDao
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER
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

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke behandling_opprettet pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke behandling_opprettet pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val behandlingId = packet["behandlingId"].asText().toUUID()
        val interneSøknadIder = packet["søknadIder"].map { it.asText().toUUID() }
        val eksterneSøknadIder = eksterneSøknadIder(interneSøknadIder).takeUnless { it.isEmpty() } ?: return sikkerlogg.error("Nå kom det en behandling_opprettet uten at vi fant eksterne søknadIder. Er ikke dét rart?")
        val tidspunkt = packet["@opprettet"].asLocalDateTime().atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingOpprettet(vedtaksperiodeId, behandlingId, tidspunkt, eksterneSøknadIder))
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingstatus(vedtaksperiodeId, behandlingId, tidspunkt, VENTER_PÅ_ARBEIDSGIVER))
    }

    private fun eksterneSøknadIder(interneSøknadIder: List<UUID>) =
        dokumentDao.finn(interneSøknadIder).filter { it.type == Dokument.Type.Søknad }.map { it.dokumentId }.toSet()

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(BehandlingOpprettetRiver::class.java)
    }
}
