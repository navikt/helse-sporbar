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

    private fun String.getSchema() = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V7)
        .getSchema(InntektsmeldingStatusTest::class.java.getResource("/json-schema/tbd.$this.json")!!.toURI())

    private val inntektsmeldingstatusSchema by lazy { "inntektsmeldingstatus".getSchema() }
    private val vedtakSchema by lazy { "vedtak".getSchema() }
    private val utbetalingSchema by lazy { "utbetaling".getSchema() }
    private val annulleringSchema by lazy { "utbetaling__annullering".getSchema() }

    private fun JsonSchema.assertSchema(json: JsonNode) {
        val valideringsfeil = validate(json)
        assertEquals(emptySet<ValidationMessage>(), valideringsfeil) { "${json.toPrettyString()}\n" }
    }

    private fun Melding.hentSchema(): Triple<String, Meldingstype, JsonSchema> = when (topic) {
        "tbd.inntektsmeldingstatus" -> Triple("sykmeldt", Meldingstype.Inntektsmeldingstatus, inntektsmeldingstatusSchema)
        "tbd.vedtak" -> Triple("fødselsnummer", Meldingstype.Vedtak, vedtakSchema)
        "tbd.utbetaling" -> when (json.path("event").asText()) {
            "utbetaling_annullert" -> Triple("fødselsnummer", Meldingstype.Annullering, annulleringSchema)
            "utbetaling_uten_utbetaling" -> Triple("fødselsnummer", Meldingstype.UtenUtbetaling, utbetalingSchema)
            else -> Triple("fødselsnummer", Meldingstype.Utbetaling, utbetalingSchema)
        }
        else -> throw IllegalStateException("Mangler schema for topic $topic")
    }.let { Triple(json.path(it.first).asText(), it.second, it.third) }

    internal fun Melding.validertJson(): JsonNode {
        if (!topic.startsWith("tbd.")) return json
        val (forventetFødselsnummer, forventetMeldingstype, schema) = hentSchema()
        assertEquals(forventetFødselsnummer, key) { "Meldinger skal publiseres med fødselsnummer som key. Key=$key, Fødselsnummer=$forventetFødselsnummer" }
        assertEquals(forventetMeldingstype, meldingstype)
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