package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class VedtakFattetRiverTest {

    companion object {
        val FØDSELSNUMMER = "12345678910"
        val ORGNUMMER = "123456789"
        val TIDSSTEMPEL = LocalDateTime.now()
        val FOM = LocalDate.of(2020, 1, 1)
        val TOM = LocalDate.of(2020, 1, 31)
        val SKJÆRINGSTIDSPUNKT = LocalDate.of(2020, 1, 1)
        val SYKEPENGEGRUNNLAG = 388260.0
        val GRUNNLAG_FOR_SYKEPENGEGRUNNLAG = 500000.0
        val GRUNNLAG_FOR_SYKEPENGEGRUNNLAG_PER_ARBEIDSGIVER = """{"12345678910":500000.0,"987654321":700000.0}"""
        val BEGRENSNING = "ER_IKKE_6G_BEGRENSET"
        val INNTEKT = 388260.0
        val AKTØRID = "123"
        val VEDTAK_FATTET_TIDSPUNKT = LocalDateTime.now()
    }

    private val testRapid = TestRapid()
    private val producerMock = mockk<KafkaProducer<String,String>>(relaxed = true)
    private val dokumentDao = DokumentDao { TestDatabase.dataSource }

    private val vedtakFattetMediator = VedtakFattetMediator(
        dokumentDao = dokumentDao,
        producer = producerMock
    )
    private val utbetalingMediator = UtbetalingMediator(
        producer = producerMock
    )

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        VedtakFattetRiver(testRapid, vedtakFattetMediator)
        UtbetalingUtbetaltRiver(testRapid, utbetalingMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `vedtakFattet uten utbetaling`() {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetUtenUtbetalingSendt(idSett)

        verify { producerMock.send( capture(captureSlot) ) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(AKTØRID, vedtakFattetJson["aktørId"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(INNTEKT, vedtakFattetJson["inntekt"].asDouble())
        assertEquals(SYKEPENGEGRUNNLAG, vedtakFattetJson["sykepengegrunnlag"].asDouble())
        assertEquals(GRUNNLAG_FOR_SYKEPENGEGRUNNLAG, vedtakFattetJson["grunnlagForSykepengegrunnlag"].asDouble())
        assertEquals(GRUNNLAG_FOR_SYKEPENGEGRUNNLAG_PER_ARBEIDSGIVER, vedtakFattetJson["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString())
        assertEquals(BEGRENSNING, vedtakFattetJson["begrensning"].asText())
        assertTrue(vedtakFattetJson.path("utbetalingId").isNull)
        assertTrue(vedtakFattetJson.path("vedtaksperiodeId").isMissingNode)
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .contains(idSett.søknadDokumentId))
    }

    @Test
    fun `vedtakFattet med utbetaling`() {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetMedUtbetalingSendt(idSett)

        verify { producerMock.send( capture(captureSlot) ) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(idSett.utbetalingId, vedtakFattetJson["utbetalingId"].let { UUID.fromString(it.asText())})
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .contains(idSett.søknadDokumentId))
    }

    @Test
    fun `vedtakFattet med begrunnelser`() {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetMedUtbetalingSendt(
            idSett,
            begrunnelser = listOf(
                Begrunnelse(
                    "SkjønnsfastsattSykepengegrunnlagFritekst", "En begrunnelse", perioder = listOf(
                        Periode(LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))
                    )
                ),
                Begrunnelse(
                    "DelvisInnvilgelse", "Du har fått delvis innvilgelse", perioder = listOf(
                        Periode(LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31))
                    )
                )
            )
        )

        verify { producerMock.send(capture(captureSlot)) }
        val vedtakFattetJson = captureSlot.last().validertJson()
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())
        assertEquals(2, vedtakFattetJson["begrunnelser"].size())
    }

    @Test
    fun `vedtakFattet med tags`() {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetMedUtbetalingSendt(idSett, tags = setOf("IngenNyArbeidsgiverperiode", "Personutbetaling", "SykepengegrunnlagUnder2G"))

        verify { producerMock.send( capture(captureSlot) ) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(idSett.utbetalingId, vedtakFattetJson["utbetalingId"].let { UUID.fromString(it.asText())})
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .contains(idSett.søknadDokumentId))

        assertEquals(listOf("IngenNyArbeidsgiverperiode", "SykepengegrunnlagUnder2G"), vedtakFattetJson["tags"].map { it.asText() })
    }

    private fun sykmeldingSendt(
        idSett: IdSett,
        hendelseIder: List<UUID> = listOf(idSett.nySøknadHendelseId)
    ) {
        testRapid.sendTestMessage(
            nySøknadMessage(
                nySøknadHendelseId = idSett.nySøknadHendelseId,
                søknadDokumentId = idSett.søknadDokumentId,
                sykmeldingDokumentId = idSett.sykmeldingDokumentId
            )
        )
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "START",
                "MOTTATT_SYKMELDING_FERDIG_GAP",
                idSett.vedtaksperiodeId,
                hendelseIder
            )
        )
    }

    private fun søknadSendt(
        idSett: IdSett,
        hendelseIder: List<UUID> = listOf(idSett.nySøknadHendelseId, idSett.sendtSøknadHendelseId)
    ) {
        testRapid.sendTestMessage(
            sendtSøknadMessage(
                sendtSøknadHendelseId = idSett.sendtSøknadHendelseId,
                søknadDokumentId = idSett.søknadDokumentId,
                sykmeldingDokumentId = idSett.sykmeldingDokumentId
            )
        )
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "MOTTATT_SYKMELDING_FERDIG_GAP",
                "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP",
                idSett.vedtaksperiodeId,
                hendelseIder
            )
        )
    }

    private fun inntektsmeldingSendt(
        idSett: IdSett,
        hendelseIder: List<UUID> = listOf(idSett.nySøknadHendelseId, idSett.inntektsmeldingHendelseId)
    ) {
        testRapid.sendTestMessage(
            inntektsmeldingMessage(
                inntektsmeldingHendelseId = idSett.inntektsmeldingHendelseId,
                inntektsmeldingDokumentId = idSett.inntektsmeldingDokumentId
            )
        )
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP",
                "AVVENTER_HISTORIKK",
                idSett.vedtaksperiodeId,
                hendelseIder
            )
        )
    }

    private fun vedtakFattetMedUtbetalingSendt(
        idSett: IdSett,
        hendelseIder: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
        ),
        begrunnelser: List<Begrunnelse> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "TIL UTBETALING",
                "AVSLUTTET",
                idSett.vedtaksperiodeId,
                hendelseIder
            )
        )
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(idSett, begrunnelser = begrunnelser, tags = tags))
    }

    private fun vedtakFattetUtenUtbetalingSendt(
        idSett: IdSett,
        hendelseIder: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
    )) {
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "TIL UTBETALING",
                "AVSLUTTET",
                idSett.vedtaksperiodeId,
                hendelseIder
            )
        )
        testRapid.sendTestMessage(vedtakFattetUtenUtbetaling(idSett))
    }

    @Language("json")
    private fun vedtakFattetUtenUtbetaling(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
    )) = """{
  "vedtaksperiodeId": "$idSett.vedtaksperiodeId",
  "fom": "$FOM",
  "tom": "$TOM",
  "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
  "hendelser": ${hendelser.map { "\"${it}\"" }},
  "sykepengegrunnlag": "$SYKEPENGEGRUNNLAG",
  "grunnlagForSykepengegrunnlag": "$GRUNNLAG_FOR_SYKEPENGEGRUNNLAG",
  "grunnlagForSykepengegrunnlagPerArbeidsgiver": $GRUNNLAG_FOR_SYKEPENGEGRUNNLAG_PER_ARBEIDSGIVER,
  "begrensning": "$BEGRENSNING",
  "inntekt": "$INNTEKT",
  "@event_name": "vedtak_fattet",
  "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec04",
  "@opprettet": "$TIDSSTEMPEL",
  "aktørId": "$AKTØRID",
  "fødselsnummer": "$FØDSELSNUMMER",
  "organisasjonsnummer": "$ORGNUMMER",
  "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
  "sykepengegrunnlagsfakta": null,
  "tags": []
}
    """

    @Language("json")
    private fun vedtakFattetMedUtbetaling(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
        ),
        vedtaksperiodeId: UUID = idSett.vedtaksperiodeId,
        utbetalingId: UUID = idSett.utbetalingId,
        begrunnelser: List<Begrunnelse> = emptyList(),
        tags: Set<String> = emptySet()
    ): String {
        val begrunnelserJson = objectMapper.writeValueAsString(begrunnelser)
        return """{
      "vedtaksperiodeId": "$vedtaksperiodeId",
      "fom": "$FOM",
      "tom": "$TOM",
      "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
      "hendelser": ${hendelser.map { "\"${it}\"" }},
      "sykepengegrunnlag": "$SYKEPENGEGRUNNLAG",
      "grunnlagForSykepengegrunnlag": "$GRUNNLAG_FOR_SYKEPENGEGRUNNLAG",
      "grunnlagForSykepengegrunnlagPerArbeidsgiver": $GRUNNLAG_FOR_SYKEPENGEGRUNNLAG_PER_ARBEIDSGIVER,
      "begrensning": "$BEGRENSNING",
      "inntekt": "$INNTEKT",
      "utbetalingId": "$utbetalingId",
      "@event_name": "vedtak_fattet",
      "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec04",
      "@opprettet": "$TIDSSTEMPEL",
      "aktørId": "$AKTØRID",
      "fødselsnummer": "$FØDSELSNUMMER",
      "organisasjonsnummer": "$ORGNUMMER",
      "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
      "sykepengegrunnlagsfakta": {
        "fastsatt": "EtterHovedregel",
        "omregnetÅrsinntekt": $GRUNNLAG_FOR_SYKEPENGEGRUNNLAG,
        "innrapportertÅrsinntekt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG + 5000},
        "avviksprosent": 12.52,
        "tags": [
          "6GBegrenset"
        ],
        "6G": 620000.0,
        "arbeidsgivere": [
          {
            "arbeidsgiver": "$ORGNUMMER",
            "omregnetÅrsinntekt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG}
          }
        ]
      },
      "begrunnelser": $begrunnelserJson,
      "tags": ${tags.map { "\"$it\"" }}
    }
        """
    }

    @Language("JSON")
    private fun nySøknadMessage(
        nySøknadHendelseId: UUID,
        sykmeldingDokumentId: UUID,
        søknadDokumentId: UUID
    ) =
        """{
            "@event_name": "ny_søknad",
            "@id": "$nySøknadHendelseId",
            "id": "$søknadDokumentId",
            "sykmeldingId": "$sykmeldingDokumentId",
            "@opprettet": "2020-06-10T10:46:46.007854"
        }"""

    @Language("JSON")
    private fun sendtSøknadMessage(
        sendtSøknadHendelseId: UUID,
        sykmeldingDokumentId: UUID,
        søknadDokumentId: UUID
    ) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "$sendtSøknadHendelseId",
            "id": "$søknadDokumentId",
            "sykmeldingId": "$sykmeldingDokumentId",
            "@opprettet": "2020-06-11T10:46:46.007854"
        }"""

    @Language("JSON")
    private fun inntektsmeldingMessage(
        inntektsmeldingHendelseId: UUID,
        inntektsmeldingDokumentId: UUID
    ) =
        """{
            "@event_name": "inntektsmelding",
            "@id": "$inntektsmeldingHendelseId",
            "inntektsmeldingId": "$inntektsmeldingDokumentId",
            "@opprettet": "2020-06-11T10:46:46.007854"
        }"""

    @Language("JSON")
    private fun vedtaksperiodeEndret(
        forrige: String,
        gjeldendeTilstand: String,
        vedtaksperiodeId: UUID,
        hendelser: List<UUID>
    ) = """{
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "organisasjonsnummer": "$ORGNUMMER",
    "gjeldendeTilstand": "$gjeldendeTilstand",
    "forrigeTilstand": "$forrige",
    "aktivitetslogg": {
        "aktiviteter": []
    },
    "vedtaksperiode_aktivitetslogg": {
        "aktiviteter": [],
        "kontekster": []
    },
    "hendelser": ${hendelser.map { "\"${it}\"" }},
    "makstid": "2020-07-12T09:20:32.262525",
    "system_read_count": 0,
    "@event_name": "vedtaksperiode_endret",
    "@id": "9154ce4d-cb8a-4dc4-96e1-379c91f76d02",
    "@opprettet": "2020-06-12T09:20:56.552561",
    "@forårsaket_av": {
        "event_name": "ny_søknad",
        "id": "75be4efa-fa13-44a9-afc2-6583dd87d626",
        "opprettet": "2020-06-12T09:20:31.985479"
    },
    "aktørId": "42",
    "fødselsnummer": "$FØDSELSNUMMER"
}
"""


private data class IdSett(
    val sykmeldingDokumentId: UUID = UUID.randomUUID(),
    val søknadDokumentId: UUID = UUID.randomUUID(),
    val inntektsmeldingDokumentId: UUID = UUID.randomUUID(),
    val nySøknadHendelseId: UUID = UUID.randomUUID(),
    val sendtSøknadHendelseId: UUID = UUID.randomUUID(),
    val inntektsmeldingHendelseId: UUID = UUID.randomUUID(),
    val vedtaksperiodeId: UUID = UUID.randomUUID(),
    val utbetalingId: UUID = UUID.randomUUID()
)
}

