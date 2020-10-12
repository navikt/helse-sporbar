package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.streams.asSequence

private const val FNR = "12020052345"
private const val ORGNUMMER = "987654321"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class EndToEndTest {
    private val testRapid = TestRapid()
    private val dataSource = setUpDatasopurceWithFlyway()
    private val dokumentDao = DokumentDao(dataSource)
    private val vedtaksperiodeDao = VedtaksperiodeDao(dataSource)
    private val vedtakDao = VedtakDao(dataSource)
    private val producer = mockk<KafkaProducer<String, JsonNode>>(relaxed = true)
    private val vedtaksperiodeMediator = VedtaksperiodeMediator(
        vedtaksperiodeDao = vedtaksperiodeDao,
        vedtakDao = vedtakDao,
        dokumentDao = dokumentDao,
        producer = producer
    )

    private lateinit var idSett: IdSett

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        VedtaksperiodeEndretRiver(testRapid, vedtaksperiodeMediator)
        UtbetaltRiver(testRapid, vedtaksperiodeMediator)
    }

    @AfterAll
    fun cleanUp() {
        dataSource.close()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        idSett = IdSett()
        clearAllMocks()
        resetDatabase()

    }

    private fun resetDatabase() {
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .also {
                it.clean()
                it.migrate()
            }
    }

    @Test
    fun `mottar sykmelding (ny søknad)`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()

        sykmeldingSendt()

        verify(exactly = 1) { producer.send(capture(slot)) }
        val vedtaksperiodeDto = slot.captured.value()
        assertEquals(
            VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon,
            VedtaksperiodeDto.TilstandDto.valueOf(vedtaksperiodeDto["tilstand"].asText())
        )
        assertEquals(2, vedtaksperiodeDto["dokumenter"].size())
    }


    @Test
    fun `ny søknad hendelse`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        sykmeldingSendt()
        søknadSendt()

        verify { producer.send(capture(slot)) }
        val vedtaksperiodeDto = slot.captured.value()
        assertEquals(
            VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon,
            VedtaksperiodeDto.TilstandDto.valueOf(vedtaksperiodeDto["tilstand"].asText())
        )
        assertEquals(2, vedtaksperiodeDto["dokumenter"].size())
    }

    @Test
    fun `utbetaling`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        sykmeldingSendt()
        søknadSendt()
        inntektsmeldingSendt()
        utbetalt()

        verify(exactly = 5) { producer.send(capture(slot)) }
        assertEquals(3, slot.captured.value()["dokumenter"].size())
    }

    @Test
    fun `automatiske utbetalinger blir merket i vedtak-event`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        sykmeldingSendt()
        søknadSendt()
        inntektsmeldingSendt()
        utbetalt(automatiskBehandling = true)

        verify(exactly = 5) { producer.send(capture(slot)) }
        assertTrue(slot.captured.value()["automatiskBehandling"].asBoolean())
    }

    @Test
    fun `tar med maksdato i utgående event`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        val maksdato = LocalDate.of(2019, 1, 1)
        sykmeldingSendt()
        søknadSendt()
        inntektsmeldingSendt()
        utbetalt(maksdato = maksdato)

        verify(exactly = 5) { producer.send(capture(slot)) }
        assertEquals(maksdato, slot.captured.value()["maksdato"].asLocalDate())
        val utbetaling = requireNotNull(finnUtbetalinger(idSett.vedtaksperiodeId))
        assertEquals(maksdato, utbetaling.maksdato)
    }

    @Test
    fun `tar med null maksdato i utgående event`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        sykmeldingSendt()
        søknadSendt()
        inntektsmeldingSendt()
        utbetalt(maksdato = null)

        verify(exactly = 5) { producer.send(capture(slot)) }
        assertTrue(slot.captured.value()["maksdato"].isMissingOrNull())
        val utbetaling = requireNotNull(finnUtbetalinger(idSett.vedtaksperiodeId))
        assertNull(utbetaling.maksdato)
    }

    @Test
    fun `påfølgende utbetaling`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        val idSett1 = IdSett()
        val idSett2 = IdSett()

        sykmeldingSendt(idSett1)
        søknadSendt(idSett1)
        inntektsmeldingSendt(idSett1)
        utbetalt(idSett1)

        verify { producer.send(capture(slot)) }
        assertTrue(slot.captured.value()["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .contains(idSett1.søknadDokumentId))

        sykmeldingSendt(idSett2)
        søknadSendt(idSett2)
        utbetalt(idSett2)

        verify { producer.send(capture(slot)) }
        assertTrue(slot.captured.value()["dokumenter"].map { UUID.fromString(it["dokumentId"].asText()) }
            .contains(idSett2.søknadDokumentId))
    }

    @Test
    fun `flere dokumenter knyttet til samme vedtak`() {
        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        val idSett1 = IdSett()
        val idSett2 = IdSett()

        sykmeldingSendt(idSett1)
        søknadSendt(idSett1)
        inntektsmeldingSendt(idSett1)
        søknadSendt(idSett2)

        utbetalt(
            idSett1, listOf(
                idSett1.nySøknadHendelseId,
                idSett1.sendtSøknadHendelseId,
                idSett1.inntektsmeldingHendelseId,
                idSett2.sendtSøknadHendelseId
            ), false
        )

        verify { producer.send(capture(slot)) }
        assertEquals(5, slot.captured.value()["dokumenter"].size())
    }

    private fun sykmeldingSendt(
        idSett: IdSett = this.idSett,
        vedtakHendelseIder: List<UUID> = listOf(idSett.nySøknadHendelseId)
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
                vedtakHendelseIder
            )
        )
    }

    private fun søknadSendt(
        idSett: IdSett = this.idSett,
        vedtakHendelseIder: List<UUID> = listOf(idSett.nySøknadHendelseId, idSett.sendtSøknadHendelseId)
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
                "AVVENTER_GAP",
                idSett.vedtaksperiodeId,
                vedtakHendelseIder
            )
        )
    }

    private fun inntektsmeldingSendt(
        idSett: IdSett = this.idSett,
        vedtakHendelseIder: List<UUID> = listOf(idSett.nySøknadHendelseId, idSett.inntektsmeldingHendelseId)
    ) {
        testRapid.sendTestMessage(
            inntektsmeldingMessage(
                inntektsmeldingHendelseId = idSett.inntektsmeldingHendelseId,
                inntektsmeldingDokumentId = idSett.inntektsmeldingDokumentId
            )
        )
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "AVVENTER_GAP",
                "AVVENTER_VILKÅRSPRØVING_GAP",
                idSett.vedtaksperiodeId,
                vedtakHendelseIder
            )
        )
    }

    private fun utbetalt(
        idSett: IdSett = this.idSett,
        vedtakHendelseIder: List<UUID> = listOf(
            idSett.nySøknadHendelseId,
            idSett.sendtSøknadHendelseId,
            idSett.inntektsmeldingHendelseId
        ),
        automatiskBehandling: Boolean = false,
        maksdato: LocalDate? = null
    ) {
        testRapid.sendTestMessage(
            vedtaksperiodeEndret(
                "TIL_UTBETALING",
                "AVSLUTTET",
                idSett.vedtaksperiodeId,
                vedtakHendelseIder
            )
        )
        testRapid.sendTestMessage(
            utbetalingMessage(
                hendelser = vedtakHendelseIder,
                automatiskBehandling = automatiskBehandling,
                maksdato = maksdato
            )
        )
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
    "fødselsnummer": "$FNR"
}
"""

    @Language("JSON")
    private fun utbetalingMessage(
        hendelser: List<UUID>,
        fom: LocalDate = LocalDate.of(2020, 6, 1),
        tom: LocalDate = LocalDate.of(2020, 6, 10),
        tidligereBrukteSykedager: Int = 0,
        automatiskBehandling: Boolean,
        maksdato: LocalDate? = null
    ) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "$FNR",
    "organisasjonsnummer": "$ORGNUMMER",
    "hendelser": ${hendelser.map { "\"${it}\"" }},
    "utbetalt": [
        {
            "mottaker": "$ORGNUMMER",
            "fagområde": "SPREF",
            "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "$fom",
                    "tom": "$tom",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": ${sykedager(fom, tom)}
                }
            ]
        },
        {
            "mottaker": "$FNR",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "$fom",
    "tom": "$tom",
    "forbrukteSykedager": ${tidligereBrukteSykedager + sykedager(fom, tom)},
    "gjenståendeSykedager": ${248 - tidligereBrukteSykedager - sykedager(fom, tom)},
    "automatiskBehandling": $automatiskBehandling,
    "maksdato": ${maksdato?.let { "\"$it\"" }},
    "opprettet": "2020-05-04T11:26:30.23846",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "${UUID.randomUUID()}",
    "@opprettet": "2020-05-04T11:27:13.521398",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

    private fun finnUtbetalinger(vedtaksperiodeId: UUID): Utbetaling? {
        @Language("PostgreSQL")
        val query = """SELECT distinct v2.vedtaksperiode_id,
                v.maksdato,
                v.fom,
                v.tom,
                v.automatisk_behandling,
                v.forbrukte_sykedager,
                v.gjenstaende_sykedager
from vedtak v
         join vedtak_hendelse vh on v.id = vh.vedtak_id
         join vedtaksperiode_hendelse h on vh.hendelse_id = h.hendelse_id
         join vedtaksperiode v2 on h.vedtaksperiode_id = v2.id
WHERE v2.vedtaksperiode_id = ?
        """

        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    vedtaksperiodeId
                ).map {
                    Utbetaling(
                        fom = LocalDate.parse(it.string("fom")),
                        tom = LocalDate.parse(it.string("tom")),
                        forbrukteSykedager = it.int("forbrukte_sykedager"),
                        gjenståendeSykedager = it.int("gjenstaende_sykedager"),
                        automatiskBehandling = it.boolean("automatisk_behandling"),
                        maksdato = it.localDate("maksdato"),
                        hendelseIder = emptyList(),
                        oppdrag = emptyList()
                    )
                }.asSingle
            )
        }
    }
}

data class IdSett(
    val sykmeldingDokumentId: UUID = UUID.randomUUID(),
    val søknadDokumentId: UUID = UUID.randomUUID(),
    val inntektsmeldingDokumentId: UUID = UUID.randomUUID(),
    val nySøknadHendelseId: UUID = UUID.randomUUID(),
    val sendtSøknadHendelseId: UUID = UUID.randomUUID(),
    val inntektsmeldingHendelseId: UUID = UUID.randomUUID(),
    val vedtaksperiodeId: UUID = UUID.randomUUID()
)
