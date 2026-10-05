package no.nav.helse.sporbar

import org.apache.kafka.common.serialization.Serializer
import tools.jackson.databind.JsonNode

internal class JsonNodeSerializer : Serializer<JsonNode> {
    override fun serialize(
        topic: String,
        data: JsonNode,
    ): ByteArray = objectMapper.writeValueAsBytes(data)
}
