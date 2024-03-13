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
import no.nav.helse.sporbar.inntektsmelding.TrengerIkkeInntektsmeldingRiver.Companion.somHarInntektsmelding
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class InntektsmeldingStatusVedtaksperiodeEndretRiver(
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
                it.demandValue("@event_name", "vedtaksperiode_endret")
                it.demand("organisasjonsnummer", JsonNode::orgnummer)
                it.requireKey("fødselsnummer", "aktørId", "vedtaksperiodeId", "hendelser")
                it.interestedIn("hendelser")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey("gjeldendeTilstand")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.error("Forstod ikke vedtaksperiode_endret (se sikkerLogg for detaljer)")
        sikkerLogg.error("Forstod ikke vedtaksperiode_endret: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        when (packet["gjeldendeTilstand"].asText()) {
            "AVSLUTTET_UTEN_UTBETALING" -> {
                logg.info("Vedtaksperiode endret til AVSLUTTET_UTEN_UTBETALING. Trenger ikke inntektsmelding. {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
                inntektsmeldingStatusMediator.lagre(packet.somTrengerIkkeInntektsmelding())
            }
            "AVVENTER_BLOKKERENDE_PERIODE" -> {
                logg.info("Vedtaksperiode endret til AVVENTER_BLOKKERENDE_PERIODE. Har inntektsmelding. {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
                inntektsmeldingStatusMediator.lagre(packet.somHarInntektsmelding())
            }
        }
    }

    private fun JsonMessage.somTrengerIkkeInntektsmelding(): TrengerIkkeInntektsmelding {
        val id = UUID.randomUUID()
        val hendelseId = UUID.fromString(this["@id"].asText())
        val fødselsnummer = this["fødselsnummer"].asText()
        val aktørId = this["aktørId"].asText()
        val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
        val organisasjonsnummer = this["organisasjonsnummer"].asText()
        val fom = this["fom"].asLocalDate()
        val tom = this["tom"].asLocalDate()
        val opprettet = this["@opprettet"].asLocalDateTime()

        return TrengerIkkeInntektsmelding(
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
internal fun JsonNode.orgnummer() { check(asText().matches("\\d{9}".toRegex())) }