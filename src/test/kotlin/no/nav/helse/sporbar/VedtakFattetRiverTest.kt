package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asInstant
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
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
import java.time.Instant
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
        val VEDTAK_FATTET_TIDSPUNKT_INSTANT = Instant.now()
    }

    @Test
    fun `ikke vedtakFattet uten utbetaling`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetUtenUtbetalingSendt(idSett)

        verify(exactly = 0) { producerMock.send(capture(captureSlot)) }
        assertTrue(captureSlot.isEmpty())
    }

    @Test
    fun `vedtakFattet med utbetaling`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
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

        assertTrue(vedtakFattetJson["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .contains(idSett.søknadDokumentId))
    }

    @Test
    fun `vedtakFattet med begrunnelser`() = e2e {
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
    fun `vedtakFattet med tags`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
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
            .contains(idSett.søknadDokumentId))

        assertEquals(listOf("IngenNyArbeidsgiverperiode", "SykepengegrunnlagUnder2G", "InntektFraAOrdningenLagtTilGrunn"), vedtakFattetJson["tags"].map { it.asText() })
    }

    @Test
    fun `vedtakFattet med utbetaling for selvstendig næringsdrivende`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        val sykepengegrunnlag = BigDecimal("777777.7")
        val beregningsgrunnlag = BigDecimal("777777.7")
        val pensjonsgivendeInntekter = listOf(2023 to BigDecimal("750000.0"), 2024 to BigDecimal("815000.0"))
        val erBegrensetTil6G = false
        val `6G` = BigDecimal("815000.0")
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetalingForSelvstendigNæringsdrivende(
                idSett = idSett,
                sykepengegrunnlag = sykepengegrunnlag,
                beregningsgrunnlag = beregningsgrunnlag,
                pensjonsgivendeInntekter = pensjonsgivendeInntekter,
                erBegrensetTil6G = erBegrensetTil6G,
                `6G` = `6G`
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
        assertEquals(VEDTAK_FATTET_TIDSPUNKT_INSTANT, vedtakFattetJson["vedtakFattetTidspunkt"].asInstant())

        assertEquals(sykepengegrunnlag, BigDecimal(vedtakFattetJson["sykepengegrunnlag"].asText()))
        assertEquals(beregningsgrunnlag, BigDecimal(vedtakFattetJson["sykepengegrunnlagsfakta"]["beregningsgrunnlag"].asText()))
        assertEquals(
            pensjonsgivendeInntekter.sortedBy { it.first },
            vedtakFattetJson["sykepengegrunnlagsfakta"]["pensjonsgivendeInntekter"].map { it["år"].asInt() to BigDecimal(it["inntekt"].asText()) }.sortedBy { it.first }
        )
        assertEquals(erBegrensetTil6G, vedtakFattetJson["sykepengegrunnlagsfakta"]["erBegrensetTil6G"].asBoolean())
        assertEquals(`6G`, BigDecimal(vedtakFattetJson["sykepengegrunnlagsfakta"]["6G"].asText()))
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
            VedtakFattetSelvstendigNæringsdrivendeRiver(testRapid, vedtakFattetMediator, speedClient)
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

    private fun E2ETestContext.sykmeldingSendt(idSett: IdSett) {
        meldinger.add(
            HentMeldingResponse(
                type = "ny_søknad",
                fnr = "",
                internDokumentId = idSett.nySøknadHendelseId,
                eksternDokumentId = idSett.sykmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "",
                jsonBody = "{}"
            )
        )
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
                jsonBody = "{}"
            )
        )
    }

    private fun E2ETestContext.inntektsmeldingSendt(idSett: IdSett) {
        meldinger.add(
            HentMeldingResponse(
                type = "inntektsmelding",
                fnr = "",
                internDokumentId = idSett.inntektsmeldingHendelseId,
                eksternDokumentId = idSett.inntektsmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "",
                jsonBody = "{}"
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

    private fun E2ETestContext.vedtakFattetUtenUtbetalingSendt(idSett: IdSett) {
        testRapid.sendTestMessage(vedtakFattetUtenUtbetaling(idSett))
    }

    @Language("json")
    private fun E2ETestContext.vedtakFattetUtenUtbetaling(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
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
    private fun E2ETestContext.vedtakFattetMedUtbetaling(
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

    @Language("json")
    private fun E2ETestContext.vedtakFattetMedUtbetalingForSelvstendigNæringsdrivende(
        idSett: IdSett,
        vedtaksperiodeId: UUID = idSett.vedtaksperiodeId,
        utbetalingId: UUID = idSett.utbetalingId,
        sykepengegrunnlag: BigDecimal,
        beregningsgrunnlag: BigDecimal,
        pensjonsgivendeInntekter: List<Pair<Int, BigDecimal>>,
        erBegrensetTil6G: Boolean,
        `6G`: BigDecimal,
        begrunnelser: List<Begrunnelse> = emptyList(),
    ): String {
        val begrunnelserJson = objectMapper.writeValueAsString(begrunnelser)
        return """{
          "@id": "${UUID.randomUUID()}",
          "@event_name": "vedtak_fattet",
          "@opprettet": "$TIDSSTEMPEL",
          "yrkesaktivitetstype": "SELVSTENDIG", 
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "fom": "$FOM",
          "tom": "$TOM",
          "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
          "sykepengegrunnlag": $sykepengegrunnlag,
          "utbetalingId": "$utbetalingId",
          "fødselsnummer": "$FØDSELSNUMMER",
          "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT_INSTANT",
          "sykepengegrunnlagsfakta": {
            "beregningsgrunnlag": $beregningsgrunnlag,
            "pensjonsgivendeInntekter": [${
            pensjonsgivendeInntekter.joinToString(separator = ",") { (år, inntekt) ->
                    """{
                            "år": $år,
                            "inntekt": $inntekt
                        }
                    """.trimIndent()
                }
            }],
            "erBegrensetTil6G": $erBegrensetTil6G,
            "6G": $`6G`
          },
          "begrunnelser": $begrunnelserJson
        }
        """
    }

    @Language("JSON")
    private fun E2ETestContext.nySøknadMessage(
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
    private fun E2ETestContext.sendtSøknadMessage(
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
    private fun E2ETestContext.inntektsmeldingMessage(
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
    private fun E2ETestContext.vedtaksperiodeEndret(
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

