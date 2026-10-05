package no.nav.helse.sporbar

import com.networknt.schema.Error
import com.networknt.schema.ExecutionContext
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialect
import com.networknt.schema.dialect.Dialects
import com.networknt.schema.format.Format
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object JsonSchemaValidator {
    private val mapper = jacksonObjectMapper()

    private object LocalDateFormat : Format {
        override fun getName() = "date"

        override fun matches(
            executionContext: ExecutionContext,
            value: String,
        ) = try {
            LocalDate.parse(value)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private val schemaRegistry =
        SchemaRegistry.withDefaultDialect(
            Dialect
                .builder(Dialects.getDraft7())
                .format(LocalDateFormat)
                .build(),
        )

    private fun String.getSchema() =
        schemaRegistry
            .getSchema(SchemaLocation.of("classpath:json-schema/tbd.$this.json"))

    private val vedtakFattetSchema by lazy { "vedtak__fattet".getSchema() }
    private val vedtakAnnullertSchema by lazy { "vedtak__annullert".getSchema() }
    private val utbetalingSchema by lazy { "utbetaling".getSchema() }
    private val annulleringSchema by lazy { "utbetaling__annullering".getSchema() }

    private fun Schema.assertSchema(json: JsonNode) {
        val valideringsfeil = validate(json)
        assertEquals(emptyList<Error>(), valideringsfeil) { "${json.toPrettyString()}\n" }
    }

    private fun Melding.hentSchema(): Pair<String, Schema> =
        when (meldingstype) {
            "VedtakFattet" -> "fødselsnummer" to vedtakFattetSchema
            "VedtakAnnullert" -> "fødselsnummer" to vedtakAnnullertSchema
            "Annullering" -> "fødselsnummer" to annulleringSchema
            "Utbetaling" -> "fødselsnummer" to utbetalingSchema
            "UtenUtbetaling" -> "fødselsnummer" to utbetalingSchema
            else -> error("Mangler schema for meldingstype $meldingstype")
        }.let { json.path(it.first).asText() to it.second }

    private fun Melding.udokumentertMelding() =
        (topic == "aapen-helse-sporbar" && meldingstype != "Annullering").also {
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

    internal fun ProducerRecord<String, String>.validertJson() =
        Melding(
            topic = topic(),
            meldingstype = headers().meldingstypeOrNull() ?: "VedtakFattet".also { require(topic() == "tbd.vedtak") },
            key = key(),
            json = mapper.readTree(value()),
        ).validertJson()
}

class Melding(
    internal val topic: String,
    internal val meldingstype: String,
    internal val key: String,
    internal val json: JsonNode,
)
