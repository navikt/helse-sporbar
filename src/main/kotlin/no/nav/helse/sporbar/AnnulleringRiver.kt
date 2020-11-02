package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import java.time.LocalDate
import java.time.LocalDateTime

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val producer: KafkaProducer<String, JsonNode>

):
    River.PacketListener {
        init {
            River(rapidsConnection).apply {
                validate {
                    it.requireValue("@event_name", "utbetaling_annullert")
                }
            }.register(this)
        }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val annullering = AnnulleringDto(
            orgnummer = packet["organisasjonsnummer"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            timestamp = packet["annullertAvSaksbehandler"].asLocalDateTime(),
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
    }

    data class AnnulleringDto(
        val orgnummer: String,
        val timestamp: LocalDateTime,
        val fødselsnummer: String,
        val fom: LocalDate?,
        val tom: LocalDate?
    )
}
