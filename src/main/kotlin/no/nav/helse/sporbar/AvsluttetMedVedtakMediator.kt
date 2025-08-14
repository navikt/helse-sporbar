package no.nav.helse.sporbar

import no.nav.helse.sporbar.dto.AvsluttetMedVedtakDto
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class AvsluttetMedVedtakMediator(
    private val producer: KafkaProducer<String, String>
) {
    internal fun avsluttetMedVedtak(avsluttetMedVedtak: AvsluttetMedVedtakDto) {
        val avsluttetMedVedtakJson = objectMapper.writeValueAsString(avsluttetMedVedtak)
        producer.send(
            ProducerRecord(
                "tbd.vedtak-v2",
                null,
                avsluttetMedVedtak.fødselsnummer,
                avsluttetMedVedtakJson,
                listOf(VedtakType.Vedtaksdata.header())
            )
        )
        sikkerLogg.info("Publiserer avsluttetMedVedtak {}", avsluttetMedVedtakJson)
    }
}

