package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import org.junit.jupiter.api.Assertions.assertEquals

internal object JsonSchemaValidator {

    private val mapper = jacksonObjectMapper()

    private fun String.getSchema() = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V7)
        .getSchema(JsonSchemaValidator::class.java.getResource("/json-schema/tbd.$this.json")!!.toURI())

    private val vedtakFattetSchema by lazy { "vedtak__fattet_arbeidstaker".getSchema() }
    private val vedtakFattetSelvstendigNæringsdrivendeSchema by lazy { "vedtak__fattet_selvstendig_næringsdrivende".getSchema() }
    private val vedtakAnnullertSchema by lazy { "vedtak__annullert".getSchema() }
    private val utbetalingSchema by lazy { "utbetaling".getSchema() }
    private val annulleringSchema by lazy { "utbetaling__annullering".getSchema() }

    private fun JsonSchema.assertSchema(json: JsonNode) {
        val valideringsfeil = validate(json)
        assertEquals(emptySet<ValidationMessage>(), valideringsfeil) { "${json.toPrettyString()}\n" }
    }

    private fun Melding.hentSchema(): Pair<String, JsonSchema> = when (meldingstype) {
        "VedtakFattet" -> "fødselsnummer" to vedtakFattetSchema
        "VedtakFattetSelvstendigNæringsdrivende" -> "fødselsnummer" to vedtakFattetSelvstendigNæringsdrivendeSchema
        "VedtakAnnullert" -> "fødselsnummer" to vedtakAnnullertSchema
        "Annullering" -> "fødselsnummer" to annulleringSchema
        "Utbetaling" -> "fødselsnummer" to utbetalingSchema
        "UtenUtbetaling" -> "fødselsnummer" to utbetalingSchema
        else -> error("Mangler schema for meldingstype $meldingstype")
    }.let { json.path(it.first).asText() to it.second }

    private fun Melding.udokumentertMelding() = (topic == "aapen-helse-sporbar" && meldingstype != "Annullering").also {
        if (it) {
            println("⚠️ Melding $meldingstype på $topic er ikke dokumentert, og blir ikke validert.")
        }
    }

    internal fun Melding.validertJson(): JsonNode {
        if (udokumentertMelding()) return json
        val (forventetFødselsnummer, schema) = hentSchema()
        assertEquals(forventetFødselsnummer, key) { "Meldinger skal publiseres med fødselsnummer som key. Key=$key, Fødselsnummer=$forventetFødselsnummer" }
        schema.assertSchema(json)
        return json
    }

    private fun Headers.meldingstypeOrNull() =
        map { it.key() to String(it.value()) }
            .singleOrNull { it.first == "type" }
            ?.second

    internal fun ProducerRecord<String, String>.validertJson() = Melding(
        topic = topic(),
        meldingstype = headers().meldingstypeOrNull() ?: "VedtakFattet".also { require(topic() == "tbd.vedtak") },
        key = key(),
        json = mapper.readTree(value())
    ).validertJson()
}

class Melding(
    internal val topic: String,
    internal val meldingstype: String,
    internal val key: String,
    internal val json: JsonNode
)
