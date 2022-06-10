package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class TrengerInntektsmeldingRiver(
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
        inntektsmeldingStatusMediator.lagre(packet.somManglerInntektsmelding())
    }

    internal companion object {
        internal fun JsonMessage.somManglerInntektsmelding(): ManglerInntektsmelding {
            val id = UUID.randomUUID()
            val hendelseId = UUID.fromString(this["@id"].asText())
            val fødselsnummer = this["fødselsnummer"].asText()
            val aktørId = this["aktørId"].asText()
            val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
            val organisasjonsnummer = this["organisasjonsnummer"].asText()
            val fom = this["fom"].asLocalDate()
            val tom = this["tom"].asLocalDate()
            val opprettet = this["@opprettet"].asLocalDateTime()

            return ManglerInntektsmelding(
                id = id,
                hendelseId = hendelseId,
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                fom = fom,
                tom = tom,
                opprettet = opprettet,
                json = this
            )
        }
    }
}
