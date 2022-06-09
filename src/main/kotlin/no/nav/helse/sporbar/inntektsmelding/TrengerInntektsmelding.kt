package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatus.TRENGER_INNTEKTSMELDING
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class TrengerInntektsmelding(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator
) : River.PacketListener{

    private val log: Logger = LoggerFactory.getLogger("sporbar")

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "trenger_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "aktørId", "vedtaksperiodeId")
                it.interestedIn("søknadIder")
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Trenger inntektsmelding for vedtaksperiode ${packet["vedtaksperiodeId"].asText()}")
        inntektsmeldingStatusMediator.lagre(packet.somInntektsmeldingPakke(status = TRENGER_INNTEKTSMELDING))
    }
}
