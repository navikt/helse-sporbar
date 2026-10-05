package no.nav.helse.sporbar.sis

import no.nav.helse.sporbar.objectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.UUID

interface SisPublisher {
    fun send(
        vedtaksperiodeId: UUID,
        melding: Behandlingstatusmelding,
    )
}

class KafkaSisPublisher(
    private val producer: KafkaProducer<String, String>,
    private val topicName: String = "tbd.sis",
) : SisPublisher {
    private companion object {
        private val mapper = objectMapper
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val Behandlingstatusmelding.json: String get() = mapper.writeValueAsString(this)
    }

    override fun send(
        vedtaksperiodeId: UUID,
        melding: Behandlingstatusmelding,
    ) {
        val meldingJson = melding.json
        producer.send(ProducerRecord(topicName, vedtaksperiodeId.toString(), meldingJson))
        sikkerLogg.info("Sender $meldingJson\nfra $melding")
    }
}
