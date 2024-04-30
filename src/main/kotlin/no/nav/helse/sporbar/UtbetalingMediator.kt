package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingMediator(
    private val producer: KafkaProducer<String, String>
) {
    internal fun utbetalingUtbetalt(utbetalingUtbetalt: UtbetalingUtbetalt) =
        send(utbetalingUtbetalt, Meldingstype.Utbetaling)

    internal fun utbetalingUtenUtbetaling(utbetalingUtbetalt: UtbetalingUtbetalt) =
        send(utbetalingUtbetalt, Meldingstype.UtenUtbetaling)

    private fun send(utbetalingUtbetalt: UtbetalingUtbetalt, meldingstype: Meldingstype) {
        val utbetalingJson = objectMapper.valueToTree<JsonNode>(utbetalingUtbetalt)
        producer.send(
            ProducerRecord(
                "tbd.utbetaling",
                null,
                utbetalingUtbetalt.fødselsnummer,
                utbetalingJson.toString(),
                listOf(meldingstype.header())
            )
        )
        sikkerLogg.info("Publiserer ${utbetalingUtbetalt.event}: {}", utbetalingJson)
    }
}

