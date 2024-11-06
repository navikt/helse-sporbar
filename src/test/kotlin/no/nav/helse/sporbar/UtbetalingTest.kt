package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        UtbetalingUtenUtbetalingRiver(testRapid, utbetalingMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `vedtakFattet med tilhørende utbetalingUtbetalt`() {
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
    fun `utbetaling - mapper ut begrunnelser på avviste dager `() {
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
    fun `utbetaling_utbetalt - mapper AndreYtelserDag hele veien ut`() {
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
    fun `utbetaling_utbetalt - mapper ArbeidIkkeGjenopptattDag hele veien ut`() {
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
    fun `utbetaling_uten_utbetaling - mapper ArbeidIkkeGjenopptattDag hele veien ut`() {
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
    fun `vedtakFattet med tilhørende utbetalingUtenUtbetaling`() {
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
    )) {
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "TIL UTBETALING",
                "AVSLUTTET",
                idSett.vedtaksperiodeId,
                hendelseIder
            )
        )
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(idSett))
    }

    private fun utbetalingUtbetaltSendt(idSett: IdSett, event: String = "utbetaling_utbetalt") {
        testRapid.sendTestMessage(utbetalingUtbetalt(idSett, event))
    }

    @Language("json")
    private fun vedtakFattetMedUtbetaling(
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
    "aktørId": "$AKTØRID",
    "fødselsnummer": "$FØDSELSNUMMER",
    "organisasjonsnummer": "$ORGNUMMER",
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
    private fun utbetalingUtbetalt(idSett: IdSett, event: String, utbetalingId: UUID = idSett.utbetalingId) = """{
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
          "type": "NavDag"
        },
        {
          "dato": "2021-01-02",
          "type": "NavHelgDag"
        },
        {
          "dato": "2021-01-03",
          "type": "NavHelgDag"
        },
        {
          "dato": "2021-01-04",
          "type": "NavDag"
        },
        {
          "dato": "2021-01-05",
          "type": "NavDag"
        },
        {
          "dato": "2021-01-06",
          "type": "NavDag"
        },
        {
          "dato": "2021-01-07",
          "type": "NavDag"
        },
        {
          "dato": "2021-01-08",
          "type": "AvvistDag", 
          "begrunnelser": ["AndreYtelserForeldrepenger"]
        }
  ],
  "@id": "1826ead5-4e9e-4670-892d-ea4ec2ffec01",
  "@opprettet": "$TIDSSTEMPEL",
  "aktørId": "$AKTØRID",
  "fødselsnummer": "$FØDSELSNUMMER",
  "organisasjonsnummer": "$ORGNUMMER"
}
    """

    @Language("json")
    private fun utbetalingUtbetaltEnAvvistDag(
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
          "type": "NavDag"
        },
        {
          "dato": "2021-05-07",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-08",
          "type": "NavHelgDag"
        },
        {
          "dato": "2021-05-09",
          "type": "NavHelgDag"
        },
        {
          "dato": "2021-05-10",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-11",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-12",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-13",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-14",
          "type": "AvvistDag",
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
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """


    @Language("json")
    private fun utbetalingUtbetaltMedArbeidIkkeGjenopptattDag() = """{
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
          "type": "ArbeidIkkeGjenopptattDag"
        },
        {
          "dato": "2021-05-07",
          "type": "Feriedag"
        },
        {
          "dato": "2021-05-08",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-09",
          "type": "NavHelgDag"
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """
    @Language("json")
    private fun utbetalingUtenUtbetalingMedArbeidIkkeGjenopptattDag() = """{
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
          "type": "ArbeidIkkeGjenopptattDag"
        },
        {
          "dato": "2022-05-07",
          "type": "Feriedag"
        },
        {
          "dato": "2022-05-08",
          "type": "NavDag"
        },
        {
          "dato": "2022-05-09",
          "type": "NavHelgDag"
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """

    @Language("json")
    private fun utbetalingUtbetaltMedAndreYtelserDag() = """{
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
          "begrunnelser": ["AndreYtelserForeldrepenger"]
        },
        {
          "dato": "2022-05-07",
          "type": "Feriedag"
        },
        {
          "dato": "2022-05-08",
          "type": "NavDag"
        },
        {
          "dato": "2022-05-09",
          "type": "NavHelgDag"
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """

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
    val utbetalingId: UUID = UUID.randomUUID(),
    val korrelasjonsId: UUID = UUID.randomUUID()
)
}
