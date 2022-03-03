package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog: Logger = LoggerFactory.getLogger("tjenestekall")

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val producer: KafkaProducer<String, JsonNode>,
    private val aivenProducer: KafkaProducer<String, JsonNode>,
) :
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_annullert")
                it.requireKey(
                    "fødselsnummer",
                    "organisasjonsnummer",
                    "tidspunkt",
                    "fom",
                    "tom",
                    "utbetalingId",
                    "korrelasjonsId"
                )
                it.interestedIn("arbeidsgiverFagsystemId", "personFagsystemId")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLog.error("forstod ikke utbetaling_annullert: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val annullering = AnnulleringDto(
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            fødselsnummer = fødselsnummer,
            tidsstempel = packet["tidspunkt"].asLocalDateTime(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            utbetalingId = UUID.fromString(packet["utbetalingId"].asText()),
            korrelasjonsId = UUID.fromString(packet["korrelasjonsId"].asText()),
            arbeidsgiverFagsystemId = packet["arbeidsgiverFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText(),
            personFagsystemId = packet["personFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()
        )
        val annulleringDto = objectMapper.valueToTree<JsonNode>(annullering)

        producer.send(
            ProducerRecord(
                "aapen-helse-sporbar",
                null,
                fødselsnummer,
                annulleringDto,
                listOf(Meldingstype.Annullering.header())
            )
        )
        aivenProducer.send(
            ProducerRecord(
                "tbd.utbetaling",
                null,
                fødselsnummer,
                annulleringDto,
                listOf(Meldingstype.Annullering.header())
            )
        )
        log.info("Publiserte annullering")
        sikkerLog.info("Publiserte annullering på $fødselsnummer")
    }

    data class AnnulleringDto(
        val utbetalingId: UUID,
        val korrelasjonsId: UUID,
        val organisasjonsnummer: String,
        val tidsstempel: LocalDateTime,
        val fødselsnummer: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val arbeidsgiverFagsystemId: String?,
        val personFagsystemId: String?) {
        val event = "utbetaling_annullert"
        @Deprecated("trengs så lenge vi produserer til on-prem")
        val orgnummer: String = organisasjonsnummer
    }
}
