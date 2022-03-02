package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog: Logger = LoggerFactory.getLogger("tjenestekall")

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val producer: KafkaProducer<String, JsonNode>,
    private val aivenProducer: KafkaProducer<String, JsonNode>,
):
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_annullert")
                it.requireKey("fødselsnummer", "organisasjonsnummer", "tidspunkt", "fom", "tom")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke utbetaling_annullert: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val annullering = AnnulleringDto(
            orgnummer = packet["organisasjonsnummer"].asText(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            tidsstempel = packet["tidspunkt"].asLocalDateTime(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate()
        )
        val annulleringDto = objectMapper.valueToTree<JsonNode>(annullering)

        producer.send(
            ProducerRecord(
                "aapen-helse-sporbar",
                null,
                fødselsnummer,
                annulleringDto,
                listOf(RecordHeader("type", Meldingstype.Annullering.name.toByteArray()))
            )
        )
        aivenProducer.send(
            ProducerRecord(
                "tbd.utbetaling",
                null,
                fødselsnummer,
                annulleringDto,
                listOf(RecordHeader("type", Meldingstype.Annullering.name.toByteArray()))
            )
        )
        log.info("Publiserte annullering")
        sikkerLog.info("Publiserte annullering på $fødselsnummer")
    }

    data class AnnulleringDto(
        @Deprecated("trengs så lenge vi produserer til on-prem")
        val orgnummer: String,
        val organisasjonsnummer: String,
        val tidsstempel: LocalDateTime,
        val fødselsnummer: String,
        val fom: LocalDate,
        val tom: LocalDate
    )
}
