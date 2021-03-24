 package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetMediator(
    private val dokumentDao: DokumentDao,
    private val producer: KafkaProducer<String, JsonNode>
) {
    internal fun vedtakFattet(vedtakFattet: VedtakFattet) {

        val dokumenter = dokumentDao.finn(vedtakFattet.hendelseIder)

        producer.send(
            ProducerRecord(
                "aapen-helse-sporbar",
                null,
                vedtakFattet.fødselsnummer,
                objectMapper.valueToTree(vedtakFattet)
            )
        )
    }
}




