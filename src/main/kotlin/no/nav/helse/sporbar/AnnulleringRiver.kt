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
    private val producer: KafkaProducer<String, JsonNode>
):
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_annullert")
                it.requireKey("fødselsnummer", "organisasjonsnummer", "annullertAvSaksbehandler", "utbetalingslinjer")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerLog.error("forstod ikke utbetaling_annullert: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val annullering = AnnulleringDto(
            orgnummer = packet["organisasjonsnummer"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            tidsstempel = packet["annullertAvSaksbehandler"].asLocalDateTime(),
            fom = packet["utbetalingslinjer"].map { it["fom"].asLocalDate() }.min(),
            tom = packet["utbetalingslinjer"].map { it["tom"].asLocalDate() }.max()
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
        log.info("Publiserte annullering")
        sikkerLog.info("Publiserte annullering på $fødselsnummer")
    }

    data class AnnulleringDto(
        val orgnummer: String,
        val tidsstempel: LocalDateTime,
        val fødselsnummer: String,
        val fom: LocalDate?,
        val tom: LocalDate?
    )
}
