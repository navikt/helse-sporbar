package no.nav.helse.sporbar.vedtaksperiodeForkastet

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatusMediator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class VedtaksperiodeForkastet(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator
) : River.PacketListener{

    private val log: Logger = LoggerFactory.getLogger("sporbar")

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "vedtaksperiode_forkastet")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "aktørId", "vedtaksperiodeId", "hendelser")
                it.interestedIn("hendelser")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
        log.info("Forkastet vedtaksperiode {}", keyValue("vedtaksperiodeId", vedtaksperiodeId))
        val vedtaksperiodeForkastetPakke = packet.somVedtaksperiodeForkastetPakke()
        inntektsmeldingStatusMediator.lagre(vedtaksperiodeForkastetPakke)
        log.info("Lagret forkastet vedtaksperiode {}", keyValue("vedtaksperiodeId", vedtaksperiodeId))
        inntektsmeldingStatusMediator.sendInntektsmeldingStatuser(vedtaksperiodeForkastetPakke)
    }
}
