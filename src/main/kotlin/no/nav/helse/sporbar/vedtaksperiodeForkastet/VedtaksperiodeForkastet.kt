package no.nav.helse.sporbar.vedtaksperiodeForkastet

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class VedtaksperiodeForkastet(
    rapidsConnection: RapidsConnection,
) : River.PacketListener{

    private val log: Logger = LoggerFactory.getLogger("sporbar")
    internal var lestMelding = false

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "vedtaksperiode_forkastet")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "aktørId", "vedtaksperiodeId")
                it.interestedIn("hendelser")
                it.interestedIn("fom", JsonNode::asLocalDate)
                it.interestedIn("tom", JsonNode::asLocalDate)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Forkastet vedtaksperiode ${packet["vedtaksperiodeId"].asText()}")
        lestMelding = true
    }
}
