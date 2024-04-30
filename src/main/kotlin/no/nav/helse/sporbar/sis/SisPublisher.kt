package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

interface SisPublisher {
    fun send(vedtaksperiodeId: UUID, melding: Behandlingstatusmelding)
}

class KafkaSisPublisher(private val producer: KafkaProducer<String, String>, private val topicName: String = "tbd.sis"): SisPublisher {
    private companion object {
        private val mapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val Behandlingstatusmelding.json: String get() = mapper.writeValueAsString(this)
    }

    override fun send(vedtaksperiodeId: UUID, melding: Behandlingstatusmelding) {
        val meldingJson = melding.json
        producer.send(ProducerRecord(topicName, vedtaksperiodeId.toString(), meldingJson))
        sikkerLogg.info("Sender $meldingJson\nfra $melding")
    }
}