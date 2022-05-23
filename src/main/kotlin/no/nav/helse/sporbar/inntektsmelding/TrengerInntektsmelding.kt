package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class TrengerInntektsmelding(
    rapidsConnection: RapidsConnection,
) : River.PacketListener{

    private val log: Logger = LoggerFactory.getLogger("sporbar")
    internal var lestEnMelding = false

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "trenger_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "vedtaksperiodeId")
                it.requireArray("søknadIder")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Trenger inntektsmelding for vedtaksperiode: {}", packet["vedtaksperiodeId"].asText())
        lestEnMelding = true
    }

}
