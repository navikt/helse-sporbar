package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Serializer

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

internal class VedtaksperiodeDtoSerializer : Serializer<VedtaksperiodeDto> {
    override fun serialize(topic: String, data: VedtaksperiodeDto): ByteArray = objectMapper.writeValueAsBytes(data)
}
