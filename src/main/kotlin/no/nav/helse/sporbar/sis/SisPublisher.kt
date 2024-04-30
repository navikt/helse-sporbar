package no.nav.helse.sporbar.sis

import java.util.UUID
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

interface SisPublisher {
    fun send(vedtaksperiodeId: UUID, melding: String)
}

class KafkaSisPublisher(private val producer: KafkaProducer<String, String>, private val topicName: String = "tbd.sis"): SisPublisher {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    override fun send(vedtaksperiodeId: UUID, melding: String) {
        producer.send(ProducerRecord(topicName, vedtaksperiodeId.toString(), melding))
        sikkerLogg.info("Sender $melding")
    }
}