package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.util.UUID
import no.nav.helse.sporbar.dto.VedtakAnnullertDto
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeAnnullertRiver(
    rapidsConnection: RapidsConnection,
    private val aivenProducer: KafkaProducer<String, String>,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "vedtaksperiode_annullert") }
            validate {
                it.requireKey(
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "vedtaksperiodeId"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("forstod ikke vedtaksperiode_annullert. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke vedtaksperiode_annullert:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val callId = packet["@id"].asText()
        withMDC("callId" to callId) {
            håndterAnnullering(packet, callId)
        }
    }
    private fun håndterAnnullering(packet: JsonMessage, callId: String) {
        val ident = packet["fødselsnummer"].asText()
        val identer = retryBlocking { speedClient.hentFødselsnummerOgAktørId(ident, callId).getOrThrow() }

        val vedtakAnnullertDto = VedtakAnnullertDto(
            fødselsnummer = identer.fødselsnummer,
            aktørId = identer.aktørId,
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate()
        )
        val vedtakAnnullertJson = objectMapper.writeValueAsString(vedtakAnnullertDto)
        aivenProducer.send(ProducerRecord("tbd.vedtak", null, identer.fødselsnummer, vedtakAnnullertJson, listOf(Meldingstype.VedtakAnnullert.header())))

        log.info("Sender vedtakAnnullert: $callId")
        sikkerLog.info("Sender vedtakAnnullert: $vedtakAnnullertJson")
    }
}