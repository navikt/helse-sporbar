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
        Meldingstype.VedtakFattet -> "fødselsnummer" to vedtakFattetSchema
        Meldingstype.VedtakFattetSelvstendigNæringsdrivende -> "fødselsnummer" to vedtakFattetSelvstendigNæringsdrivendeSchema
        Meldingstype.VedtakAnnullert -> "fødselsnummer" to vedtakAnnullertSchema
        Meldingstype.Behandlingstilstand -> error("Mangler schema for meldingstype $meldingstype")
        Meldingstype.Annullering -> "fødselsnummer" to annulleringSchema
        Meldingstype.Utbetaling -> "fødselsnummer" to utbetalingSchema
        Meldingstype.UtenUtbetaling -> "fødselsnummer" to utbetalingSchema
        Meldingstype.Inntektsmeldingstatus -> "sykmeldt" to inntektsmeldingstatusSchema
    }.let { json.path(it.first).asText() to it.second }

    private fun Melding.udokumentertMelding() = (topic == "aapen-helse-sporbar" && meldingstype != Meldingstype.Annullering).also {
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
