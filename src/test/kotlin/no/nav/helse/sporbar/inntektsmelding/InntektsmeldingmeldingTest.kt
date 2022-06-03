package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InntektsmeldingmeldingTest {

    @Test
    fun `demonstrerer enkel validering`() {
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(
                InntektsmeldingmeldingTest::class.java.getResource("/inntektsmelding/im-status-schema-1.0.0.json")
                    .toURI()
            )
        val fails = schema.validate(jacksonObjectMapper().readTree(håndlagetKorrektJson))

        assertEquals(emptySet<ValidationMessage>(), fails)
    }
}

val håndlagetKorrektJson = """
    {
    "status": "TRENGER_IKKE_INNTEKTSMELDING",
    "sykmeldt": "dette er et fødselsnummer",
    "arbeidsgiver": "flagg som sier at det er en brukerutbetaling",
    "vedtaksperiode": "${UUID.randomUUID()}",
    "fom": "${LocalDate.now()}",
    "tom": "${LocalDate.now()}",
    "id": "${UUID.randomUUID()}",
    "tidsstempel": "${LocalDateTime.now()}",
    "versjon": "1.0.0"
    }
""".trimIndent()