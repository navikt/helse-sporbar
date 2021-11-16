package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingMediator(
    private val producer: KafkaProducer<String, JsonNode>
) {
    internal fun utbetalingUtbetalt(utbetalingUtbetalt: UtbetalingUtbetalt) {
        val utbetalingJson = objectMapper.valueToTree<JsonNode>(utbetalingUtbetalt)
        producer.send(
            ProducerRecord(
                "tbd.utbetaling",
                null,
                utbetalingUtbetalt.fødselsnummer,
                utbetalingJson
            )
        )
        sikkerLogg.info("Publiserer ${utbetalingUtbetalt.event}: {}", utbetalingJson)
    }
}

