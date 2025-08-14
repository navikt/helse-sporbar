package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson
import no.nav.helse.sporbar.dto.Utbetalingsdag
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AvsluttetMedVedtakRiverTest {

    companion object {
        val FØDSELSNUMMER = "12345678910"
        val ORGNUMMER = "123456789"
        val YRKESAKTIVITETSTYPE = "ARBEIDSTAKER"
        val TIDSSTEMPEL = LocalDateTime.now()
        val FOM = LocalDate.of(2020, 1, 1)
        val TOM = LocalDate.of(2020, 1, 2)
        val SKJÆRINGSTIDSPUNKT = LocalDate.of(2020, 1, 1)
        val SYKEPENGEGRUNNLAG = 388260.0
        val VEDTAK_FATTET_TIDSPUNKT = LocalDateTime.now()
        val AUTOMATISKBEHANDLING = true
        val FORBRUKTE_SYKEDAGER = 10
        val GJENSTÅENDESYKEDAGER = 238
        val FORELØPIGBEREGNETSLUTTPÅSYKEPENGER = LocalDate.of(2020, 12, 31)
    }

    @Test
    fun `avsluttet med vedtak - e2e`() = e2e {
        val captureSlot = mutableListOf<ProducerRecord<String, String>>()
        val idSett = IdSett()

        avsluttetMedVedtak(idSett)

        verify { producerMock.send(capture(captureSlot)) }

        val avsluttetMedVedtak = captureSlot.last()
        assertEquals(FØDSELSNUMMER, avsluttetMedVedtak.key())

        val avsluttetMedVedtakJson = avsluttetMedVedtak.validertJson()

        val utbetalingsdager = avsluttetMedVedtakJson["utbetalingsdager"].map {
            Utbetalingsdag(
                dato = it["dato"].asLocalDate(),
                type = it["type"].asText(),
                beløpTilArbeidsgiver = it["beløpTilArbeidsgiver"].asInt(),
                beløpTilBruker = it["beløpTilBruker"].asInt(),
                sykdomsgrad = it["sykdomsgrad"].asInt(),
                begrunnelser = it["begrunnelser"].takeUnless(JsonNode::isMissingOrNull)?.map { begrunnelse ->
                    begrunnelse.asText()
                }
            )
        }

        assertEquals(FØDSELSNUMMER, avsluttetMedVedtakJson["fødselsnummer"].textValue())
        assertEquals(FOM, avsluttetMedVedtakJson["fom"].asLocalDate())
        assertEquals(TOM, avsluttetMedVedtakJson["tom"].asLocalDate())
        assertEquals(SKJÆRINGSTIDSPUNKT, avsluttetMedVedtakJson["skjæringstidspunkt"].asLocalDate())
        assertEquals(VEDTAK_FATTET_TIDSPUNKT, avsluttetMedVedtakJson["vedtakFattetTidspunkt"].asLocalDateTime())

        assertEquals(AUTOMATISKBEHANDLING, avsluttetMedVedtakJson["automatiskBehandling"].asBoolean())
        assertEquals(FORBRUKTE_SYKEDAGER, avsluttetMedVedtakJson["forbrukteSykedager"].asInt())
        assertEquals(GJENSTÅENDESYKEDAGER, avsluttetMedVedtakJson["gjenståendeSykedager"].asInt())
        assertEquals(FORELØPIGBEREGNETSLUTTPÅSYKEPENGER, avsluttetMedVedtakJson["foreløpigBeregnetSluttPåSykepenger"].asLocalDate())
        assertEquals(2, utbetalingsdager.size)

    }

    private data class E2ETestContext(val testRapid: TestRapid) {
        val producerMock = mockk<KafkaProducer<String, String>>(relaxed = true)

        val avsluttetMedVedtakMediator = AvsluttetMedVedtakMediator(producerMock)

        init {
            AvsluttetMedVedtakRiver(testRapid, avsluttetMedVedtakMediator)
        }
    }

    private fun e2e(testblokk: E2ETestContext.() -> Unit) {
        val testRapid = TestRapid()
        testblokk(E2ETestContext(testRapid))
    }

    private fun E2ETestContext.avsluttetMedVedtak(
        idSett: IdSett,
    ) {
        testRapid.sendTestMessage(vedtakAvsluttet(idSett))
    }

    @Language("json")
    private fun vedtakAvsluttet(
        idSett: IdSett,
        hendelser: List<UUID> = listOf(UUID.randomUUID())
    ): String {
        return """{
            "@id": "1826ead5-5e9e-4670-892d-ea4ec2ffec04", 
            "@event_name": "avsluttet_med_vedtak",
            "@opprettet": "$TIDSSTEMPEL",
            "fødselsnummer": "$FØDSELSNUMMER",
            "organisasjonsnummer": "$ORGNUMMER",
            "yrkesaktivitetstype": "$YRKESAKTIVITETSTYPE",
            "vedtaksperiodeId": "${idSett.vedtaksperiodeId}",
            "behandlingId": "${idSett.behandlingId}",
            "fom": "$FOM",
            "tom": "$TOM",
            "skjæringstidspunkt": "$SKJÆRINGSTIDSPUNKT",
            "hendelser": ${hendelser.map { "\"${it}\"" }},
            "sykepengegrunnlag": "$SYKEPENGEGRUNNLAG",
            "sykepengegrunnlagsfakta": {
                "fastsatt": "EtterHovedregel",
                "omregnetÅrsinntekt": $SYKEPENGEGRUNNLAG,
                "6G": 620000.0,
                "arbeidsgivere": [
                    {
                        "arbeidsgiver": "$ORGNUMMER",
                        "omregnetÅrsinntekt": $SYKEPENGEGRUNNLAG,
                        "inntektskilde": "Arbeidsgiver"
                    }
                ]
            },
            "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
            "automatiskBehandling": $AUTOMATISKBEHANDLING,
            "forbrukteSykedager": $FORBRUKTE_SYKEDAGER,
            "gjenståendeSykedager": $GJENSTÅENDESYKEDAGER ,
            "foreløpigBeregnetSluttPåSykepenger": "$FORELØPIGBEREGNETSLUTTPÅSYKEPENGER",
            "utbetalingsdager": [
                {
                    "dato": "2020-01-01",
                    "type": "ArbeidsgiverperiodeDag",
                    "beløpTilArbeidsgiver": 0,
                    "beløpTilBruker": 0,
                    "sykdomsgrad": 100,
                    "begrunnelser": null
                },
                {
                    "dato": "2020-01-02",
                    "type": "ArbeidsgiverperiodeDag",
                    "beløpTilArbeidsgiver": 0,
                    "beløpTilBruker": 0,
                    "sykdomsgrad": 100,
                    "begrunnelser": null
                }
            ]
        }
        """
    }

    private data class IdSett(
        val vedtaksperiodeId: UUID = UUID.randomUUID(),
        val behandlingId: UUID = UUID.randomUUID(),
    )
}

