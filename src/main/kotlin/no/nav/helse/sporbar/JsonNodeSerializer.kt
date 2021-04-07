package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.common.serialization.Serializer

internal class JsonNodeSerializer : Serializer<JsonNode> {
    override fun serialize(topic: String, data: JsonNode): ByteArray = objectMapper.writeValueAsBytes(data)
}
