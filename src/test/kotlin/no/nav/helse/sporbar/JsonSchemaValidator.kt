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

    private val inntektsmeldingstatusSchema by lazy { "inntektsmeldingstatus".getSchema() }
    private val vedtakFattetSchema by lazy { "vedtak__fattet".getSchema() }
    private val vedtakAnnullertSchema by lazy { "vedtak__annullert".getSchema() }
    private val utbetalingSchema by lazy { "utbetaling".getSchema() }
    private val annulleringSchema by lazy { "utbetaling__annullering".getSchema() }

    private fun JsonSchema.assertSchema(json: JsonNode) {
        val valideringsfeil = validate(json)
        assertEquals(emptySet<ValidationMessage>(), valideringsfeil) { "${json.toPrettyString()}\n" }
    }

    private fun Melding.hentSchema(): Triple<String, Meldingstype, JsonSchema> = when (topic) {
        "tbd.inntektsmeldingstatus" -> Triple("sykmeldt", Meldingstype.Inntektsmeldingstatus, inntektsmeldingstatusSchema)
        "tbd.vedtak" -> when (json.path("event").asText()) {
            "vedtak_annullert" -> Triple("fødselsnummer", Meldingstype.VedtakAnnullert, vedtakAnnullertSchema)
            else -> Triple("fødselsnummer", Meldingstype.VedtakFattet, vedtakFattetSchema)
        }
        "aapen-helse-sporbar" -> Triple("fødselsnummer", Meldingstype.Annullering, annulleringSchema)
        "tbd.utbetaling" -> when (json.path("event").asText()) {
            "utbetaling_annullert" -> Triple("fødselsnummer", Meldingstype.Annullering, annulleringSchema)
            "utbetaling_uten_utbetaling" -> Triple("fødselsnummer", Meldingstype.UtenUtbetaling, utbetalingSchema)
            else -> Triple("fødselsnummer", Meldingstype.Utbetaling, utbetalingSchema)
        }
        else -> throw IllegalStateException("Mangler schema for topic $topic")
    }.let { Triple(json.path(it.first).asText(), it.second, it.third) }

    private fun Melding.udokumentertMelding() = (topic == "aapen-helse-sporbar" && meldingstype != Meldingstype.Annullering).also { if (it) {
        println("⚠️ Melding $meldingstype på $topic er ikke dokumentert, og blir ikke validert.")
    }}

    internal fun Melding.validertJson(): JsonNode {
        if (udokumentertMelding()) return json
        val (forventetFødselsnummer, forventetMeldingstype, schema) = hentSchema()
        assertEquals(forventetFødselsnummer, key) { "Meldinger skal publiseres med fødselsnummer som key. Key=$key, Fødselsnummer=$forventetFødselsnummer" }
        assertEquals(forventetMeldingstype, meldingstype)
        schema.assertSchema(json)
        return json
    }

    private fun Headers.meldingstypeOrNull() =
        map { it.key() to String(it.value()) }
        .singleOrNull { it.first == "type" }
        ?.let { Meldingstype.valueOf(it.second) }

    internal fun ProducerRecord<String, String>.validertJson() = Melding(
        topic = topic(),
        meldingstype = headers().meldingstypeOrNull() ?: Meldingstype.VedtakFattet.also { require(topic() == "tbd.vedtak") },
        key = key(),
        json = mapper.readTree(value())
    ).validertJson()
}

class Melding(
    internal val topic: String,
    internal val meldingstype: Meldingstype,
    internal val key: String,
    internal val json: JsonNode
)