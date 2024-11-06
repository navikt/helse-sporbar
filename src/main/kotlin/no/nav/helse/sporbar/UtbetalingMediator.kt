package no.nav.helse.sporbar

import no.nav.helse.sporbar.dto.UtbetalingUtbetaltDto
import no.nav.helse.sporbar.dto.UtbetalingUtenUtbetalingDto
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingMediator(
    private val producer: KafkaProducer<String, String>
) {
    internal fun utbetalingUtbetalt(utbetalingUtbetalt: UtbetalingUtbetaltDto) =
        send(utbetalingUtbetalt.fødselsnummer, utbetalingUtbetalt, Meldingstype.Utbetaling)

    internal fun utbetalingUtenUtbetaling(utbetalingUtbetalt: UtbetalingUtenUtbetalingDto) =
        send(utbetalingUtbetalt.fødselsnummer, utbetalingUtbetalt, Meldingstype.UtenUtbetaling)

    private fun <T> send(key: String, utbetalingUtbetalt: T, meldingstype: Meldingstype) {
        val utbetalingJson = objectMapper.writeValueAsString(utbetalingUtbetalt)
        producer.send(ProducerRecord("tbd.utbetaling", null, key, utbetalingJson, listOf(meldingstype.header())))
        sikkerLogg.info("Publiserer ${meldingstype}: {}", utbetalingJson)
    }
}

