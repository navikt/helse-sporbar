package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
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
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class UtbetalingTest {

    companion object {
        val FØDSELSNUMMER = "12345678910"
        val UTBETALINGSTYPE = "UTBETALING"
        val ORGNUMMER = "123456789"
        val TIDSSTEMPEL = LocalDateTime.now()
        val FOM = LocalDate.of(2021, 1, 1)
        val TOM = LocalDate.of(2021, 1, 7)
        val MAKSDATO = LocalDate.of(2020, 5, 10)
        val SKJÆRINGSTIDSPUNKT = LocalDate.of(2020, 1, 1)
        val SYKEPENGEGRUNNLAG = 388260.0
        val GRUNNLAG_FOR_SYKEPENGEGRUNNLAG = 500000.0
        val GRUNNLAG_FOR_SYKEPENGEGRUNNLAG_PER_ARBEIDSGIVER = """{"12345678910":500000.0,"987654321":700000.0}"""
        val BEGRENSNING = "ER_IKKE_6G_BEGRENSET"
        val INNTEKT = 388260.0
        val AKTØRID = "123"
        val FORBRUKTESYKEDAGER = 217
        val GJENSTÅENDESYKEDAGER =  31
        val AUTOMATISK_BEHANDLING = true
        val NETTOBELØP = 38360
        val VEDTAK_FATTET_TIDSPUNKT = LocalDateTime.now()
    }

    @Test
    fun `vedtakFattet med tilhørende utbetalingUtbetalt`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetMedUtbetalingSendt(idSett)
        utbetalingUtbetaltSendt(idSett)

        verify { producerMock.send( capture(captureSlot) ) }

        val utbetalingUtbetalt = captureSlot.last()
        val vedtakFattet = captureSlot[captureSlot.size-2]

        assertEquals(FØDSELSNUMMER, utbetalingUtbetalt.key())

        val utbetalingUtbetaltJson = utbetalingUtbetalt.validertJson()
        val vedtakFattetJson = vedtakFattet.validertJson()

        assertEquals(idSett.utbetalingId, utbetalingUtbetaltJson["utbetalingId"].let { UUID.fromString(it.asText())})
        assertEquals(idSett.korrelasjonsId, utbetalingUtbetaltJson["korrelasjonsId"].let { UUID.fromString(it.asText())})
        assertEquals(utbetalingUtbetaltJson["utbetalingId"].asText(), vedtakFattetJson["utbetalingId"].asText())

        assertEquals("utbetaling_utbetalt", utbetalingUtbetaltJson["event"].textValue())
        assertEquals(FØDSELSNUMMER, utbetalingUtbetaltJson["fødselsnummer"].textValue())
        assertEquals(UTBETALINGSTYPE, utbetalingUtbetaltJson["type"].textValue())
        assertEquals(AKTØRID, utbetalingUtbetaltJson["aktørId"].textValue())
        assertEquals(FOM, utbetalingUtbetaltJson["fom"].asLocalDate())
        assertEquals(TOM, utbetalingUtbetaltJson["tom"].asLocalDate())
        assertEquals(FORBRUKTESYKEDAGER, utbetalingUtbetaltJson["forbrukteSykedager"].asInt())
        assertEquals(GJENSTÅENDESYKEDAGER, utbetalingUtbetaltJson["gjenståendeSykedager"].asInt())
        assertEquals(AUTOMATISK_BEHANDLING, utbetalingUtbetaltJson["automatiskBehandling"].asBoolean())
        assertEquals(NETTOBELØP, utbetalingUtbetaltJson["arbeidsgiverOppdrag"]["nettoBeløp"].asInt())
        assertEquals(FOM, utbetalingUtbetaltJson["arbeidsgiverOppdrag"]["utbetalingslinjer"].first()["fom"].asLocalDate())
        assertEquals(TOM, utbetalingUtbetaltJson["arbeidsgiverOppdrag"]["utbetalingslinjer"].first()["tom"].asLocalDate())
        assertEquals(MAKSDATO, utbetalingUtbetaltJson.path("foreløpigBeregnetSluttPåSykepenger").asLocalDate())
        assertTrue(utbetalingUtbetaltJson.path("utbetalingsdager").toList().isNotEmpty())
        assertTrue(utbetalingUtbetaltJson.path("vedtaksperiodeId").isMissingNode)
    }

    @Test
    fun `utbetaling - mapper ut begrunnelser på avviste dager `() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        testRapid.sendTestMessage(utbetalingUtbetaltEnAvvistDag())
        verify { producerMock.send( capture(captureSlot) ) }

        val utbetalingUtbetalt = captureSlot.last()
        val utbetalingUtbetaltJson = utbetalingUtbetalt.validertJson()

        val avvistDag = utbetalingUtbetaltJson.path("utbetalingsdager").toList().last()
            .path("begrunnelser").toList().map { it.asText() }
        assertEquals(
            listOf(
                "EtterDødsdato",
                "MinimumInntekt",
                "Over70",
                "MinimumInntektOver67",
                "SykepengedagerOppbruktOver67",
                "AndreYtelserAap",
                "AndreYtelserDagpenger",
                "AndreYtelserForeldrepenger",
                "AndreYtelserOmsorgspenger",
                "AndreYtelserOpplaringspenger",
                "AndreYtelserPleiepenger",
                "AndreYtelserSvangerskapspenger",
            ),
            avvistDag
        )
    }

    @Test
    fun `utbetaling_utbetalt - mapper AndreYtelserDag hele veien ut`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        testRapid.sendTestMessage(utbetalingUtbetaltMedAndreYtelserDag())
        verify { producerMock.send( capture(captureSlot) ) }

        val utbetalingUtbetalt = captureSlot.last()
        val utbetalingUtbetaltJson = utbetalingUtbetalt.validertJson()

        val utbetalingsdager = utbetalingUtbetaltJson.path("utbetalingsdager").associate { it["dato"].asLocalDate() to it["type"].asText() }
        assertEquals(mapOf(
            LocalDate.parse("2022-05-06") to "AndreYtelser",
            LocalDate.parse("2022-05-07") to "Feriedag",
            LocalDate.parse("2022-05-08") to "NavDag",
            LocalDate.parse("2022-05-09") to "NavHelgDag"
        ), utbetalingsdager)
    }

    @Test
    fun `utbetaling_utbetalt - mapper ArbeidIkkeGjenopptattDag hele veien ut`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        testRapid.sendTestMessage(utbetalingUtbetaltMedArbeidIkkeGjenopptattDag())
        verify { producerMock.send( capture(captureSlot) ) }

        val utbetalingUtbetalt = captureSlot.last()
        val utbetalingUtbetaltJson = utbetalingUtbetalt.validertJson()

        val utbetalingsdager = utbetalingUtbetaltJson.path("utbetalingsdager").associate { it["dato"].asLocalDate() to it["type"].asText() }
        assertEquals(mapOf(
            LocalDate.parse("2021-05-06") to "ArbeidIkkeGjenopptattDag",
            LocalDate.parse("2021-05-07") to "Feriedag",
            LocalDate.parse("2021-05-08") to "NavDag",
            LocalDate.parse("2021-05-09") to "NavHelgDag"
        ), utbetalingsdager)
    }

    @Test
    fun `utbetaling_uten_utbetaling - mapper ArbeidIkkeGjenopptattDag hele veien ut`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        testRapid.sendTestMessage(utbetalingUtenUtbetalingMedArbeidIkkeGjenopptattDag())
        verify { producerMock.send( capture(captureSlot) ) }

        val utbetalingUtenUtbetaling = captureSlot.last()
        val utbetalingUtenUtbetalingJson = utbetalingUtenUtbetaling.validertJson()

        val utbetalingsdager = utbetalingUtenUtbetalingJson.path("utbetalingsdager").associate { it["dato"].asLocalDate() to it["type"].asText() }
        assertEquals(mapOf(
            LocalDate.parse("2022-05-06") to "ArbeidIkkeGjenopptattDag",
            LocalDate.parse("2022-05-07") to "Feriedag",
            LocalDate.parse("2022-05-08") to "NavDag",
            LocalDate.parse("2022-05-09") to "NavHelgDag"
        ), utbetalingsdager)
    }

    @Test
    fun `vedtakFattet med tilhørende utbetalingUtenUtbetaling`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        sykmeldingSendt(idSett)
        søknadSendt(idSett)
        inntektsmeldingSendt(idSett)
        vedtakFattetMedUtbetalingSendt(idSett)
        utbetalingUtbetaltSendt(idSett, "utbetaling_uten_utbetaling")

        verify { producerMock.send( capture(captureSlot) ) }

        val utbetalingUtbetalt = captureSlot.last()
        assertEquals(FØDSELSNUMMER, utbetalingUtbetalt.key())
        val utbetalingUtbetaltJson = utbetalingUtbetalt.validertJson()

        assertEquals("utbetaling_uten_utbetaling", utbetalingUtbetaltJson["event"].textValue())
    }

    private data class E2ETestContext(val testRapid: TestRapid) {
        val meldinger = mutableListOf<HentMeldingResponse>()
        val producerMock = mockk<KafkaProducer<String,String>>(relaxed = true)
        val spedisjonClient = mockk<SpedisjonClient> {
            every {
                hentMeldinger(any(), any())
            } returns HentMeldingerResponse(meldinger).ok()
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
            UtbetalingUtenUtbetalingRiver(testRapid, utbetalingMediator, speedClient)

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
        meldinger.add(HentMeldingResponse(
            type = "ny_søknad",
            fnr = "",
            internDokumentId = idSett.nySøknadHendelseId,
            eksternDokumentId = idSett.sykmeldingDokumentId,
            rapportertDato = LocalDateTime.now(),
            duplikatkontroll = "",
            jsonBody = "{}"
        ))
    }

    private fun E2ETestContext.søknadSendt(idSett: IdSett) {
        meldinger.add(HentMeldingResponse(
            type = "sendt_søknad_nav",
            fnr = "",
            internDokumentId = idSett.sendtSøknadHendelseId,
            eksternDokumentId = idSett.søknadDokumentId,
            rapportertDato = LocalDateTime.now(),
            duplikatkontroll = "",
            jsonBody = "{}"
        ))
    }

    private fun E2ETestContext.inntektsmeldingSendt(idSett: IdSett) {
        meldinger.add(HentMeldingResponse(
            type = "inntektsmelding",
            fnr = "",
            internDokumentId = idSett.inntektsmeldingHendelseId,
            eksternDokumentId = idSett.inntektsmeldingDokumentId,
            rapportertDato = LocalDateTime.now(),
            duplikatkontroll = "",
            jsonBody = "{}"
        ))
    }

    private fun E2ETestContext.vedtakFattetMedUtbetalingSendt(idSett: IdSett) {
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(idSett))
    }

    private fun E2ETestContext.utbetalingUtbetaltSendt(idSett: IdSett, event: String = "utbetaling_utbetalt") {
        testRapid.sendTestMessage(utbetalingUtbetalt(idSett, event))
    }

    @Language("json")
    private fun E2ETestContext.vedtakFattetMedUtbetaling(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
        ),
        vedtaksperiodeId: UUID = idSett.vedtaksperiodeId,
        utbetalingId: UUID = idSett.utbetalingId) = """{
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
    "tags": [],
    "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
    "sykepengegrunnlagsfakta": {
        "fastsatt": "EtterSkjønn",
        "omregnetÅrsinntekt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG},
        "skjønnsfastsatt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG + 2500},
        "innrapportertÅrsinntekt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG + 5000},
        "avviksprosent": 26.52,
        "6G": 620000.0,
        "tags": [],
        "arbeidsgivere": [{
            "arbeidsgiver": "${ORGNUMMER}",
            "omregnetÅrsinntekt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG},
            "skjønnsfastsatt": ${GRUNNLAG_FOR_SYKEPENGEGRUNNLAG + 2500}
        }]
    }
}
    """


    @Language("json")
    private fun E2ETestContext.utbetalingUtbetalt(idSett: IdSett, event: String, utbetalingId: UUID = idSett.utbetalingId) = """{
  "utbetalingId": "$utbetalingId",
  "korrelasjonsId": "${idSett.korrelasjonsId}",
  "fom": "$FOM",
  "tom": "$TOM",
  "maksdato": "$MAKSDATO",
  "forbrukteSykedager": "$FORBRUKTESYKEDAGER",
  "gjenståendeSykedager": "$GJENSTÅENDESYKEDAGER",
  "stønadsdager": 35,
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "$UTBETALINGSTYPE",
  "tidspunkt": "$TIDSSTEMPEL",
  "automatiskBehandling": "$AUTOMATISK_BEHANDLING",
  "@event_name": "$event",
  "arbeidsgiverOppdrag": {
    "mottaker": "$ORGNUMMER",
    "fagområde": "SPREF",
    "linjer": [
      {
        "fom": "$FOM",
        "tom": "$TOM",
        "sats": 1431,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "$TIDSSTEMPEL",
    "nettoBeløp": "$NETTOBELØP",
    "stønadsdager": 35,
    "fom": "$FOM",
    "tom": "$TOM"
  },
  "personOppdrag": {
    "mottaker": "$ORGNUMMER",
    "fagområde": "SP",
    "linjer": [
      {
        "fom": "$FOM",
        "tom": "$TOM",
        "sats": 1431,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "$TIDSSTEMPEL",
    "nettoBeløp": "$NETTOBELØP",
    "stønadsdager": 35,
    "fom": "$FOM",
    "tom": "$TOM"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-01-01",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-02",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-03",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-04",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-05",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-06",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-07",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-01-08",
          "type": "AvvistDag", 
          "begrunnelser": ["AndreYtelserForeldrepenger"],
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 0
        }
  ],
  "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec01",
  "@opprettet": "$TIDSSTEMPEL",
  "fødselsnummer": "$FØDSELSNUMMER",
  "organisasjonsnummer": "$ORGNUMMER"
}
    """

    @Language("json")
    private fun E2ETestContext.utbetalingUtbetaltEnAvvistDag(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "korrelasjonsId": "${UUID.randomUUID()}",
  "@event_name": "utbetaling_utbetalt",
  "fom": "2021-05-06",
  "tom": "2021-05-13",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "stønadsdager": 35,
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "REVURDERING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [
      {
        "fom": "2021-05-06",
        "tom": "2021-05-13",
        "sats": 1431,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-05-06",
    "tom": "2021-05-13"
  },
  "personOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SP",
    "linjer": [
      {
        "fom": "2021-05-06",
        "tom": "2021-05-13",
        "sats": 1431,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-05-06",
    "tom": "2021-05-13"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-05-06",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
          
        },
        {
          "dato": "2021-05-07",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-08",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-09",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-10",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-11",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-12",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-13",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 1431,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-14",
          "type": "AvvistDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100,
          "begrunnelser": [
              "EtterDødsdato", 
              "MinimumInntekt", 
              "Over70", 
              "MinimumInntektOver67", 
              "SykepengedagerOppbruktOver67", 
              "AndreYtelserAap",
              "AndreYtelserDagpenger",
              "AndreYtelserForeldrepenger",
              "AndreYtelserOmsorgspenger",
              "AndreYtelserOpplaringspenger",
              "AndreYtelserPleiepenger",
              "AndreYtelserSvangerskapspenger"
          ]
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "organisasjonsnummer": "123456789"
}
    """


    @Language("json")
    private fun E2ETestContext.utbetalingUtbetaltMedArbeidIkkeGjenopptattDag() = """{
  "@id": "${UUID.randomUUID()}",
  "fødselsnummer": "12345678910",
  "utbetalingId": "${UUID.randomUUID()}",
  "korrelasjonsId": "${UUID.randomUUID()}",
  "@event_name": "utbetaling_utbetalt",
  "fom": "2021-05-06",
  "tom": "2021-05-13",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "stønadsdager": 35,
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "REVURDERING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [
      {
        "fom": "2021-05-06",
        "tom": "2021-05-13",
        "sats": 1431,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-05-06",
    "tom": "2021-05-13"
  },
  "personOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SP",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "NY",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": 0,
    "stønadsdager": 0,
    "fom": "${LocalDate.MIN}",
    "tom": "${LocalDate.MAX}"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-05-06",
          "type": "ArbeidIkkeGjenopptattDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-07",
          "type": "Feriedag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 0
        },
        {
          "dato": "2021-05-08",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2021-05-09",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 0
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "organisasjonsnummer": "123456789"
}
    """
    @Language("json")
    private fun E2ETestContext.utbetalingUtenUtbetalingMedArbeidIkkeGjenopptattDag() = """{
  "@id": "${UUID.randomUUID()}",
  "fødselsnummer": "12345678910",
  "utbetalingId": "${UUID.randomUUID()}",
  "korrelasjonsId": "${UUID.randomUUID()}",
  "@event_name": "utbetaling_uten_utbetaling",
  "fom": "-999999999-01-01",
  "tom": "+999999999-12-31",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "stønadsdager": 35,
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "REVURDERING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "NY",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": 0,
    "stønadsdager": 0,
    "fom": "-999999999-01-01",
    "tom": "-999999999-01-01"
  },
  "personOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SP",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "NY",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": 0,
    "stønadsdager": 0,
    "fom": "-999999999-01-01",
    "tom": "-999999999-01-01"
  },
  "utbetalingsdager": [
        {
          "dato": "2022-05-06",
          "type": "ArbeidIkkeGjenopptattDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 0
        },
        {
          "dato": "2022-05-07",
          "type": "Feriedag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 0
        },
        {
          "dato": "2022-05-08",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431,
          "beløpTilBruker": 0,
          "sykdomsgrad": 50
        },
        {
          "dato": "2022-05-09",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 50
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "organisasjonsnummer": "123456789"
}
    """

    @Language("json")
    private fun E2ETestContext.utbetalingUtbetaltMedAndreYtelserDag() = """{
  "@id": "${UUID.randomUUID()}",
  "fødselsnummer": "12345678910",
  "utbetalingId": "${UUID.randomUUID()}",
  "korrelasjonsId": "${UUID.randomUUID()}",
  "@event_name": "utbetaling_utbetalt",
  "fom": "-999999999-01-01",
  "tom": "+999999999-12-31",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "stønadsdager": 35,
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "REVURDERING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "NY",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": 0,
    "stønadsdager": 0,
    "fom": "-999999999-01-01",
    "tom": "-999999999-01-01"
  },
  "personOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SP",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "NY",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": 0,
    "stønadsdager": 0,
    "fom": "-999999999-01-01",
    "tom": "-999999999-01-01"
  },
  "utbetalingsdager": [
        {
          "dato": "2022-05-06",
          "type": "AndreYtelser",
          "begrunnelser": ["AndreYtelserForeldrepenger"],
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2022-05-07",
          "type": "Feriedag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 0
        },
        {
          "dato": "2022-05-08",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        },
        {
          "dato": "2022-05-09",
          "type": "NavHelgDag",
          "beløpTilArbeidsgiver": 0,
          "beløpTilBruker": 0,
          "sykdomsgrad": 100
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "organisasjonsnummer": "123456789"
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
        val utbetalingId: UUID = UUID.randomUUID(),
        val korrelasjonsId: UUID = UUID.randomUUID()
    )
}
