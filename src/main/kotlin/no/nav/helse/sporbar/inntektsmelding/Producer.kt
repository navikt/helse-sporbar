package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.sporbar.Meldingstype
import no.nav.helse.sporbar.inntektsmelding.Producer.Melding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

internal interface Producer {
    fun send(melding: Melding)
    class Melding(
        internal val topic: String,
        internal val meldingstype: Meldingstype,
        internal val key: String,
        internal val json: JsonNode
    )
}

internal class Kafka(
    private val kafkaProducer: KafkaProducer<String, JsonNode>
) : Producer {
    override fun send(melding: Melding) {
        kafkaProducer.send(
            ProducerRecord(
                melding.topic,
                null,
                melding.key,
                melding.json,
                listOf(melding.meldingstype.header())
            )
        )
    }
}