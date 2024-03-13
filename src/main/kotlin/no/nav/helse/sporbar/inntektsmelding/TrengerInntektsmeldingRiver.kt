package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class TrengerInntektsmeldingRiver(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator
) : River.PacketListener{

    private companion object {
        private val logg: Logger = LoggerFactory.getLogger(this::class.java)
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "trenger_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "aktørId", "vedtaksperiodeId")
                it.interestedIn("søknadIder")
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.error("Forstod ikke trenger_inntektsmelding (se sikkerLogg for detaljer)")
        sikkerLogg.error("Forstod ikke trenger_inntektsmelding: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("Trenger inntektsmelding for vedtaksperiode {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
        inntektsmeldingStatusMediator.lagre(packet.somManglerInntektsmelding())
    }

    private fun JsonMessage.somManglerInntektsmelding(): ManglerInntektsmelding {
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
