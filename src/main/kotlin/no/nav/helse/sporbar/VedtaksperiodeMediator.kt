package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)


internal class VedtaksperiodeMediator(
    val vedtaksperiodeDao: VedtaksperiodeDao,
    val producer: KafkaProducer<String, VedtaksperiodeDto>
) {
    fun publiserEndring(vedtaksperiodeId: UUID) {
        log.info("skulle ha publisert et event på kø")

//        producer.send(objectMapper.writeValue())

    }

}

internal fun Vedtaksperiode.toDto() : VedtaksperiodeDto {
    TODO()
}
