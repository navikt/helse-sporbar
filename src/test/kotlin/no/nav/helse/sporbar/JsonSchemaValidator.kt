package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatusTest
import no.nav.helse.sporbar.inntektsmelding.Producer.Melding
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals

internal object JsonSchemaValidator {

    private val inntektsmeldingstatusSchema by lazy {
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(InntektsmeldingStatusTest::class.java.getResource("/inntektsmelding/im-status-schema-1.0.0.json")!!.toURI())
    }

    private fun JsonSchema.assertSchema(json: JsonNode) {
        val valideringsfeil = validate(json)
        assertEquals(emptySet<ValidationMessage>(), valideringsfeil)
    }

    internal fun Melding.validertJson(): JsonNode {
        if (!topic.startsWith("tbd.")) return json
        val (fødselsnummerKey, schema) = when (topic) {
            "tbd.inntektsmeldingstatus" -> "sykmeldt" to inntektsmeldingstatusSchema
            else -> throw IllegalStateException("Mangler schema for topic $topic")
        }
        assertEquals(json.path(fødselsnummerKey).asText(), key) { "Meldinger skal publiseres med fødselsnummer som key." }
        schema.assertSchema(json)
        return json
    }

    internal fun ProducerRecord<String, JsonNode>.validertJson() = Melding(
        topic = topic(),
        meldingstype = Meldingstype.valueOf(String(headers().lastHeader("type").value())),
        key = key(),
        json = value()
    ).validertJson()
}