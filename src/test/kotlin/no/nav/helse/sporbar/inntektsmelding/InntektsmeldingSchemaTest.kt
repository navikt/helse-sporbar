package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatus.FORKASTET
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatus.TRENGER_IKKE_INNTEKTSMELDING
import no.nav.helse.sporbar.inntektsmelding.InntektsmeldingStatus.TRENGER_INNTEKTSMELDING
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class InntektsmeldingSchemaTest {

    private val objectMapper = jacksonObjectMapper()
    private val schema by lazy {
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(InntektsmeldingSchemaTest::class.java.getResource("/inntektsmelding/im-status-schema-1.0.0.json")!!.toURI())
    }

    @Test
    fun `gyldig melding`() {
        @Language("JSON")
        val json = """
        {
            "status": "MANGLER_IKKE_INNTEKTSMELDING",
            "sykmeldt": "dette er et fødselsnummer",
            "arbeidsgiver": "flagg som sier at det er en brukerutbetaling",
            "vedtaksperiode": "${UUID.randomUUID()}",
            "fom": "${LocalDate.now()}",
            "tom": "${LocalDate.now()}",
            "id": "${UUID.randomUUID()}",
            "tidsstempel": "${LocalDateTime.now()}",
            "versjon": "1.0.0"
        }
        """
        assertSchema(json)
    }

    @Test
    fun `trenger inntektsmelding`() {
        val id = UUID.fromString("a7172c5b-e40c-4e08-a9a9-17948393c5c8")
        val pakke = InntektsmeldingPakke(
            id = UUID.randomUUID(),
            hendelseId = UUID.randomUUID(),
            fødselsnummer = "11111111111",
            aktørId = "22222222222",
            organisasjonsnummer = "333333333",
            vedtaksperiodeId = UUID.fromString("ed6a3551-a5d9-49ef-ada8-15a5eebbcf64"),
            status = TRENGER_INNTEKTSMELDING,
            fom = LocalDate.parse("2018-01-01"),
            tom = LocalDate.parse("2018-05-05"),
            opprettet = LocalDateTime.parse("2022-06-07T10:56:41.356835"),
            json = JsonMessage("{}", MessageProblems("{}"))
        )
        @Language("JSON")
        val forventet = """
        {
            "id": "a7172c5b-e40c-4e08-a9a9-17948393c5c8",
            "status": "MANGLER_INNTEKTSMELDING",
            "sykmeldt": "11111111111",
            "arbeidsgiver": "333333333",
            "vedtaksperiode": "ed6a3551-a5d9-49ef-ada8-15a5eebbcf64",
            "tidsstempel": "2022-06-07T10:56:41.356835",
            "fom": "2018-01-01",
            "tom": "2018-05-05",
            "versjon": "1.0.0"
        }
        """
        val eksternDto = (id to pakke).tilEksternDto()
        assertJson(forventet, eksternDto)
        assertSchema(eksternDto)
    }

    @Test
    fun `trenger ikke inntektsmelding`() {
        val id = UUID.fromString("42ff2da7-e62f-4e45-8f84-86cbb7e2d148")
        val pakke = InntektsmeldingPakke(
            id = UUID.randomUUID(),
            hendelseId = UUID.randomUUID(),
            fødselsnummer = "11111111112",
            aktørId = "22222222222",
            organisasjonsnummer = "333333334",
            vedtaksperiodeId = UUID.fromString("1ae47dbc-eb0c-41cd-89c5-d6b404634955"),
            status = TRENGER_IKKE_INNTEKTSMELDING,
            fom = LocalDate.parse("2019-01-01"),
            tom = LocalDate.parse("2019-05-05"),
            opprettet = LocalDateTime.parse("2022-06-08T10:56:41.356835"),
            json = JsonMessage("{}", MessageProblems("{}"))
        )
        @Language("JSON")
        val forventet = """
        {
            "id": "42ff2da7-e62f-4e45-8f84-86cbb7e2d148",
            "status": "MANGLER_IKKE_INNTEKTSMELDING",
            "sykmeldt": "11111111112",
            "arbeidsgiver": "333333334",
            "vedtaksperiode": "1ae47dbc-eb0c-41cd-89c5-d6b404634955",
            "tidsstempel": "2022-06-08T10:56:41.356835",
            "fom": "2019-01-01",
            "tom": "2019-05-05",
            "versjon": "1.0.0"
        }
        """
        val eksternDto = (id to pakke).tilEksternDto()
        assertJson(forventet, eksternDto)
        assertSchema(eksternDto)
    }

    @Test
    fun `vedtaksperiode forkastet`() {
        val id = UUID.fromString("1be7aafb-64b4-4135-8c2e-a8ea25d0b7bc")
        val pakke = InntektsmeldingPakke(
            id = UUID.randomUUID(),
            hendelseId = UUID.randomUUID(),
            fødselsnummer = "11111111113",
            aktørId = "22222222222",
            organisasjonsnummer = "333333335",
            vedtaksperiodeId = UUID.fromString("7358b190-742c-4da0-8346-594c7caa61b3"),
            status = FORKASTET,
            fom = LocalDate.parse("2021-01-01"),
            tom = LocalDate.parse("2021-05-05"),
            opprettet = LocalDateTime.parse("2021-06-08T10:56:41.356835"),
            json = JsonMessage("{}", MessageProblems("{}"))
        )
        @Language("JSON")
        val forventet = """
        {
            "id": "1be7aafb-64b4-4135-8c2e-a8ea25d0b7bc",
            "status": "BEHANDLES_UTENFOR_SPLEIS",
            "sykmeldt": "11111111113",
            "arbeidsgiver": "333333335",
            "vedtaksperiode": "7358b190-742c-4da0-8346-594c7caa61b3",
            "tidsstempel": "2021-06-08T10:56:41.356835",
            "fom": "2021-01-01",
            "tom": "2021-05-05",
            "versjon": "1.0.0"
        }
        """
        val eksternDto = (id to pakke).tilEksternDto()
        assertJson(forventet, eksternDto)
        assertSchema(eksternDto)
    }

    private fun assertSchema(json: String) {
        val valideringsfeil = schema.validate(objectMapper.readTree(json))
        assertEquals(emptySet<ValidationMessage>(), valideringsfeil)
    }

    private fun assertJson(forventet: String, faktisk: String) = JSONAssert.assertEquals(forventet, faktisk, true)
}