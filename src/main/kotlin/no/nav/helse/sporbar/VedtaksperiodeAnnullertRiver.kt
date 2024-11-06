package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import java.time.LocalDate
import java.util.UUID
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeAnnullertRiver(
    rapidsConnection: RapidsConnection,
    private val aivenProducer: KafkaProducer<String, String>
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_annullert")
                it.requireKey(
                    "aktørId",
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

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke vedtaksperiode_annullert. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke vedtaksperiode_annullert:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val vedtakAnnullertDto = VedtakAnnullertDto(
            fødselsnummer = fødselsnummer,
            aktørId = packet["aktørId"].asText(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate()
        )
        val vedtakAnnullertJson = objectMapper.valueToTree<JsonNode>(vedtakAnnullertDto)
        aivenProducer.send(
            ProducerRecord(
                "tbd.vedtak",
                null,
                fødselsnummer,
                vedtakAnnullertJson.toString(),
                listOf(Meldingstype.VedtakAnnullert.header())
            )
        )

        log.info("Sender vedtakAnnullert: ${packet["@id"].asText()}")
        sikkerLog.info("Sender vedtakAnnullert: $vedtakAnnullertJson")
    }

    data class VedtakAnnullertDto(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val fom: LocalDate,
        val tom: LocalDate,
        val event: String = "vedtak_annullert",
        val versjon: String = "1.0.0"
    )
}