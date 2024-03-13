package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class InntektsmeldingStatusVedtaksperiodeForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator
): River.PacketListener {

    private companion object {
        private val logg: Logger = LoggerFactory.getLogger(this::class.java)
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_forkastet")
                it.demand("organisasjonsnummer", JsonNode::orgnummer)
                it.requireKey("fødselsnummer", "aktørId", "vedtaksperiodeId", "hendelser")
                it.interestedIn("hendelser")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.error("Forstod ikke vedtaksperiode_forkastet (se sikkerLogg for detaljer)")
        sikkerLogg.error("Forstod ikke vedtaksperiode_forkastet: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("Vedtaksperiode forkastet. Behandles utenfor Spleis. {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
        inntektsmeldingStatusMediator.lagre(packet.somBehandlesUtenforSpleis())
    }

    private fun JsonMessage.somBehandlesUtenforSpleis(): BehandlesUtenforSpleis {
        val id = UUID.randomUUID()
        val hendelseId = UUID.fromString(this["@id"].asText())
        val fødselsnummer = this["fødselsnummer"].asText()
        val aktørId = this["aktørId"].asText()
        val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
        val organisasjonsnummer = this["organisasjonsnummer"].asText()
        val fom = this["fom"].asLocalDate()
        val tom = this["tom"].asLocalDate()
        val opprettet = this["@opprettet"].asLocalDateTime()

        return BehandlesUtenforSpleis(
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
