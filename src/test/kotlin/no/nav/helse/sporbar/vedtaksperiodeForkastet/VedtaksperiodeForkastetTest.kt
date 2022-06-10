package no.nav.helse.sporbar.vedtaksperiodeForkastet

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sporbar.TestDatabase
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingDao
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingSchemaTest
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatusMediator
import no.nav.helse.sporbar.inntektsmelding.Producer
import no.nav.helse.sporbar.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VedtaksperiodeForkastetTest {

    private val schema by lazy {
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(InntektsmeldingSchemaTest::class.java.getResource("/inntektsmelding/im-status-schema-1.0.0.json")!!.toURI())
    }

    private val testRapid = TestRapid()
    private val fnr = "12345678910"
    private val aktørId = "1427484794278"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()
    private val vedtaksperiodeId = UUID.randomUUID()

    private val testProducer = object : Producer {
        val publiserteMeldinger = mutableMapOf<String, String>()
        override fun send(key: String, value: String) {
            publiserteMeldinger[key] = value
        }
    }
    private val inntektsmeldingDao = InntektsmeldingDao(TestDatabase.dataSource)
    private val vedtaksperiodeForkastetDao = VedtaksperiodeForkastetDao(TestDatabase.dataSource)
    private val mediator = InntektsmeldingStatusMediator(inntektsmeldingDao, vedtaksperiodeForkastetDao, mockk(relaxed = true), testProducer)

    init {
        VedtaksperiodeForkastet(testRapid, mediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        testProducer.publiserteMeldinger.clear()
    }

    @Test
    fun `Kan lese vedtaksperiode_forkastet og publisere på eksternt format`() {
        assertNull(finn(vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeForkastet(vedtaksperiodeId))
        val melding = finn(vedtaksperiodeId)
        val json = objectMapper.readTree(melding)
        assertSchema(json)
        assertEquals(vedtaksperiodeId, UUID.fromString(json.path("vedtaksperiode").path("id").asText()))
        assertNotNull(LocalDate.parse(json.path("vedtaksperiode").path("fom").asText()))
        assertNotNull(LocalDate.parse(json.path("vedtaksperiode").path("tom").asText()))
        assertNotNull(LocalDateTime.parse(json.path("tidsstempel").asText()))
        assertEquals(json.path("status").asText(), "BEHANDLES_UTENFOR_SPLEIS")
        assertEquals(fnr, json.path("sykmeldt").asText())
        assertEquals(orgnr, json.path("arbeidsgiver").asText())
        assertEquals("1.0.0", json.path("versjon").asText())
    }

    private fun vedtaksperiodeForkastet(vedtaksperiodeId: UUID): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to "vedtaksperiode_forkastet",
                "@id" to UUID.randomUUID(),
                "vedtaksperiodeId" to vedtaksperiodeId,
                "fødselsnummer" to fnr,
                "aktørId" to aktørId,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to LocalDate.now(),
                "tom" to LocalDate.now().plusDays(10),
                "hendelser" to setOf(UUID.randomUUID())
            )
        ).toJson()
    }

    private fun finn(vedtaksperiodeId: UUID) =
        testProducer.publiserteMeldinger.filterValues { UUID.fromString(objectMapper.readTree(it).path("vedtaksperiode").path("id").asText()) == vedtaksperiodeId }.values.singleOrNull()

    private fun assertSchema(json: JsonNode) {
        val valideringsfeil = schema.validate(json)
        assertEquals(emptySet<ValidationMessage>(), valideringsfeil)
    }
}
