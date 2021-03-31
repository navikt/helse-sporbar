package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingMediator(
    private val producer: KafkaProducer<String, JsonNode>
) {
    internal fun utbetalingUtbetalt(utbetalingUtbetalt: UtbetalingUtbetalt) {

        val meldingForEkstern = oversett(utbetalingUtbetalt)

        producer.send(
            ProducerRecord(
                "aapen-helse-sporbar",
                null,
                utbetalingUtbetalt.fødselsnummer,
                objectMapper.valueToTree(meldingForEkstern
                )
            )
        )

        sikkerLogg.info("Publiserer {}", StructuredArguments.keyValue("utbetalingUtbetalt", meldingForEkstern))
    }

    private fun oversett(utbetalingUtbetalt: UtbetalingUtbetalt): UtbetalingUtbetaltForEksternDTO {
        return UtbetalingUtbetaltForEksternDTO(
            utbetalingId = utbetalingUtbetalt.utbetalingId,
            fødselsnummer = utbetalingUtbetalt.fødselsnummer,
            aktørId = utbetalingUtbetalt.aktørId,
            organisasjonsnummer = utbetalingUtbetalt.organisasjonsnummer,
            fom = utbetalingUtbetalt.fom,
            tom = utbetalingUtbetalt.tom,
            forbrukteSykedager = utbetalingUtbetalt.forbrukteSykedager,
            gjenståendeSykedager = utbetalingUtbetalt.gjenståendeSykedager,
            automatiskBehandling = utbetalingUtbetalt.automatiskBehandling,
            arbeidsgiverOppdrag = utbetalingUtbetalt.arbeidsgiverOppdrag
        )
    }
}

 data class UtbetalingUtbetaltForEksternDTO(
     val utbetalingId: UUID,
     val fødselsnummer: String,
     val aktørId: String,
     val organisasjonsnummer: String,
     val fom: LocalDate,
     val tom: LocalDate,
     val forbrukteSykedager: Int,
     val gjenståendeSykedager: Int,
     val automatiskBehandling: Boolean,
     val arbeidsgiverOppdrag: UtbetalingUtbetalt.OppdragDto
     )
