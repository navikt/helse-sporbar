package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.result_object.ok
import com.github.navikt.tbd_libs.spedisjon.HentMeldingResponse
import com.github.navikt.tbd_libs.spedisjon.HentMeldingerResponse
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import com.github.navikt.tbd_libs.speed.IdentResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private data class NavnOgIdent(
    val navn: String,
    val ident: String
)

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
        private val saksbehandler = NavnOgIdent("Ole Saksbehandler", "Z123456")
        private val beslutter = NavnOgIdent("Kari Beslutter", "X654321")
    }

    @Test
    fun `ikke vedtakFattet uten utbetaling`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        søknadSendt(idSett)
        vedtakFattetUtenUtbetalingSendt(idSett)

        verify(exactly = 0) { producerMock.send(capture(captureSlot)) }
        assertTrue(captureSlot.isEmpty())
    }

    @Test
    fun `vedtakFattet med utbetaling`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        søknadSendt(idSett)
        vedtakFattetMedUtbetalingSendt(idSett)

        verify { producerMock.send(capture(captureSlot)) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(idSett.utbetalingId, vedtakFattetJson["utbetalingId"].let { UUID.fromString(it.asText()) })
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())
        assertEquals(saksbehandler.navn, vedtakFattetJson["saksbehandler"]["navn"].asText())
        assertEquals(saksbehandler.ident, vedtakFattetJson["saksbehandler"]["ident"].asText())
        assertEquals(beslutter.navn, vedtakFattetJson["beslutter"]["navn"].asText())
        assertEquals(beslutter.ident, vedtakFattetJson["beslutter"]["ident"].asText())

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .containsAll(listOf(idSett.søknadDokumentId, idSett.sykmeldingDokumentId)))
    }

    @Test
    fun `automatisert vedtak med utbetaling`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        søknadSendt(idSett)
        automatisertVedtakMedUtbetalingSendt(idSett)

        verify { producerMock.send(capture(captureSlot)) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(idSett.utbetalingId, vedtakFattetJson["utbetalingId"].let { UUID.fromString(it.asText()) })
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())
        assertEquals(null, vedtakFattetJson["saksbehandler"].takeUnless { it.isMissingOrNull() })
        assertEquals(null, vedtakFattetJson["beslutter"].takeUnless { it.isMissingOrNull() })

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .containsAll(listOf(idSett.søknadDokumentId, idSett.sykmeldingDokumentId)))
    }

    @Test
    fun `vedtakFattet med begrunnelser`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        søknadSendt(idSett)
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
    fun `vedtakFattet med tags`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        søknadSendt(idSett)
        vedtakFattetMedUtbetalingSendt(idSett, tags = setOf("IngenNyArbeidsgiverperiode", "Personutbetaling", "SykepengegrunnlagUnder2G", "InntektFraAOrdningenLagtTilGrunn"))

        verify { producerMock.send(capture(captureSlot)) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(idSett.utbetalingId, vedtakFattetJson["utbetalingId"].let { UUID.fromString(it.asText()) })
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .containsAll(listOf(idSett.søknadDokumentId, idSett.sykmeldingDokumentId)))

        assertEquals(listOf("IngenNyArbeidsgiverperiode", "SykepengegrunnlagUnder2G", "InntektFraAOrdningenLagtTilGrunn"), vedtakFattetJson["tags"].map { it.asText() })
    }

    @Test
    fun `vedtakFattet med utbetaling for selvstendig næringsdrivende`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        søknadSendt(idSett)

        val sykepengegrunnlag = BigDecimal("777777.7")
        val beregningsgrunnlag = BigDecimal("777777.7")
        val erBegrensetTil6G = false
        val seksG = BigDecimal("815000.0")
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetalingForSelvstendigNæringsdrivende(
                idSett = idSett,
                sykepengegrunnlag = sykepengegrunnlag,
                beregningsgrunnlag = beregningsgrunnlag,
                erBegrensetTil6G = erBegrensetTil6G,
                seksG = seksG
            )
        )

        verify { producerMock.send(capture(captureSlot)) }

        val vedtakFattet = captureSlot.last()
        assertEquals(FØDSELSNUMMER, vedtakFattet.key())

        val vedtakFattetJson = vedtakFattet.validertJson()
        assertEquals(FØDSELSNUMMER, vedtakFattetJson["fødselsnummer"].textValue())
        assertEquals(FOM, vedtakFattetJson["fom"].asLocalDate())
        assertEquals(TOM, vedtakFattetJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, vedtakFattetJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(idSett.utbetalingId, vedtakFattetJson["utbetalingId"].let { UUID.fromString(it.asText()) })
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, vedtakFattetJson["vedtakFattetTidspunkt"].asLocalDateTime())

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .containsAll(listOf(idSett.søknadDokumentId, idSett.sykmeldingDokumentId)))

        assertEquals(sykepengegrunnlag, BigDecimal(vedtakFattetJson["sykepengegrunnlag"].asText()))
        assertEquals(beregningsgrunnlag, BigDecimal(vedtakFattetJson["sykepengegrunnlagsfakta"]["selvstendig"]["beregningsgrunnlag"].asText()))
        assertEquals(emptyList<String>(), vedtakFattetJson["sykepengegrunnlagsfakta"]["tags"].map { it.asText() })
        assertEquals(seksG, BigDecimal(vedtakFattetJson["sykepengegrunnlagsfakta"]["6G"].asText()))
    }

    private data class E2ETestContext(val testRapid: TestRapid) {
        val meldinger = mutableListOf<HentMeldingResponse>()
        val producerMock = mockk<KafkaProducer<String, String>>(relaxed = true)
        val spedisjonClient = mockk<SpedisjonClient> {
            every { hentMeldinger(any(), any()) } returns HentMeldingerResponse(meldinger).ok()
        }

        val vedtakFattetMediator = VedtakFattetMediator(
            spedisjonClient = spedisjonClient,
            producer = producerMock
        )
        val utbetalingMediator = UtbetalingMediator(
            producer = producerMock
        )
        val speedClient = mockk<SpeedClient>()

        init {
            VedtakFattetRiver(testRapid, vedtakFattetMediator, speedClient)
            UtbetalingUtbetaltRiver(testRapid, utbetalingMediator, speedClient)

            every { speedClient.hentFødselsnummerOgAktørId(any(), any()) } returns IdentResponse(
                fødselsnummer = FØDSELSNUMMER,
                aktørId = AKTØRID,
                npid = null,
                kilde = IdentResponse.KildeResponse.PDL
            ).ok()
        }
    }

    private fun e2e(testblokk: E2ETestContext.() -> Unit) {
        val testRapid = TestRapid()
        testblokk(E2ETestContext(testRapid))
    }

    private fun E2ETestContext.søknadSendt(idSett: IdSett) {
        meldinger.add(
            HentMeldingResponse(
                type = "sendt_søknad_nav",
                fnr = "",
                internDokumentId = idSett.sendtSøknadHendelseId,
                eksternDokumentId = idSett.søknadDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "",
                jsonBody = """{
                        "sykmeldingId": "${idSett.sykmeldingDokumentId}"
                    }"""
            )
        )
    }

    private fun E2ETestContext.vedtakFattetMedUtbetalingSendt(
        idSett: IdSett,
        begrunnelser: List<Begrunnelse> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(idSett, begrunnelser = begrunnelser, tags = tags))
    }

    private fun E2ETestContext.automatisertVedtakMedUtbetalingSendt(
        idSett: IdSett,
    ) {
        testRapid.sendTestMessage(automatisertVedtakMedUtbetaling(idSett))
    }

    private fun E2ETestContext.vedtakFattetUtenUtbetalingSendt(idSett: IdSett) {
        testRapid.sendTestMessage(vedtakFattetUtenUtbetaling(idSett))
    }

    @Language("json")
    private fun vedtakFattetUtenUtbetaling(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
        )
    ) = """{
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
      "fødselsnummer": "$FØDSELSNUMMER",
      "organisasjonsnummer": "$ORGNUMMER",
      "yrkesaktivitetstype": "ARBEIDSTAKER",
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
      "saksbehandler": {
        "navn": "${saksbehandler.navn}",
        "ident": "${saksbehandler.ident}"
      },
      "beslutter": {
        "navn": "${beslutter.navn}",
        "ident": "${beslutter.ident}"
      },
      "begrunnelser": $begrunnelserJson,
      "tags": ${tags.map { "\"$it\"" }}
    }
        """
    }

    @Language("json")
    private fun automatisertVedtakMedUtbetaling(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
        ),
        vedtaksperiodeId: UUID = idSett.vedtaksperiodeId,
        utbetalingId: UUID = idSett.utbetalingId,
    ): String {
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
      "fødselsnummer": "$FØDSELSNUMMER",
      "organisasjonsnummer": "$ORGNUMMER",
      "yrkesaktivitetstype": "ARBEIDSTAKER",
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
      "saksbehandler": null,
      "beslutter": null,
      "begrunnelser": [],
      "tags": []
    }
        """
    }

    @Language("json")
    private fun vedtakFattetMedUtbetalingForSelvstendigNæringsdrivende(
        idSett: IdSett,
        vedtaksperiodeId: UUID = idSett.vedtaksperiodeId,
        utbetalingId: UUID = idSett.utbetalingId,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
        ),
        sykepengegrunnlag: BigDecimal,
        beregningsgrunnlag: BigDecimal,
        erBegrensetTil6G: Boolean,
        seksG: BigDecimal,
        begrunnelser: List<Begrunnelse> = emptyList(),
        tags: Set<String> = emptySet(),
    ): String {
        val begrunnelserJson = objectMapper.writeValueAsString(begrunnelser)
        return """{
      "vedtaksperiodeId": "$vedtaksperiodeId",
      "fom": "$FOM",
      "tom": "$TOM",
      "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
      "hendelser": ${hendelser.map { "\"${it}\"" }},
      "sykepengegrunnlag": "$sykepengegrunnlag",
      "grunnlagForSykepengegrunnlag": "$GRUNNLAG_FOR_SYKEPENGEGRUNNLAG",
      "grunnlagForSykepengegrunnlagPerArbeidsgiver": $GRUNNLAG_FOR_SYKEPENGEGRUNNLAG_PER_ARBEIDSGIVER,
      "begrensning": "$BEGRENSNING",
      "inntekt": "$INNTEKT",
      "utbetalingId": "$utbetalingId",
      "@event_name": "vedtak_fattet",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "$TIDSSTEMPEL",
      "fødselsnummer": "$FØDSELSNUMMER",
      "organisasjonsnummer": "SELVSTENDIG",
      "yrkesaktivitetstype": "SELVSTENDIG",
      "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
      "sykepengegrunnlagsfakta": {
        "fastsatt": "EtterHovedregel",
        "6G": $seksG,
        "omregnetÅrsinntekt": $GRUNNLAG_FOR_SYKEPENGEGRUNNLAG,
        "innrapportertÅrsinntekt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG + 5000},
        "avviksprosent": 12.52,
        "tags": [ ${if (erBegrensetTil6G) {"\"6GBegrenset\""} else ""} ],
        "selvstendig": { 
          "beregningsgrunnlag": $beregningsgrunnlag,
          "pensjonsgivendeInntekter": [
            {
              "årstall": 2024,
              "beløp": 600000.0
            },
            {
              "årstall": 2023,
              "beløp": 600000.1
            },
            {
              "årstall": 2022,
              "beløp": 600000.2
            }
          ]
        }
      },
      "saksbehandler": {
        "navn": "${saksbehandler.navn}",
        "ident": "${saksbehandler.ident}"
      },
      "beslutter": {
        "navn": "${beslutter.navn}",
        "ident": "${beslutter.ident}"
      },
      "begrunnelser": $begrunnelserJson,
      "tags": ${tags.map { "\"$it\"" }}
    }
        """
    }

    private data class IdSett(
        val sykmeldingDokumentId: UUID = UUID.randomUUID(),
        val søknadDokumentId: UUID = UUID.randomUUID(),
        val nySøknadHendelseId: UUID = UUID.randomUUID(),
        val sendtSøknadHendelseId: UUID = UUID.randomUUID(),
        val vedtaksperiodeId: UUID = UUID.randomUUID(),
        val utbetalingId: UUID = UUID.randomUUID()
    )
}

