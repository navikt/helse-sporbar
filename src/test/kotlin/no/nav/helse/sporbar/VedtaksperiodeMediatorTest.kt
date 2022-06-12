package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sporbar.JsonSchemaValidator.validertJson

private const val FNR = "fnr"
private const val ORGNUMMER = "orgnr"

internal class VedtaksperiodeMediatorTest {

    private val vedtaksperiodeDao = mockk<VedtaksperiodeDao>(relaxed = true)
    private val vedtakDao = mockk<VedtakDao>(relaxed = true)
    private val dokumentDao = mockk<DokumentDao>(relaxed = true)
    private val producer = mockk<KafkaProducer<String, JsonNode>>(relaxed = true)
    private val vedtaksperiodeMediator = VedtaksperiodeMediator(
        vedtaksperiodeDao = vedtaksperiodeDao,
        vedtakDao = vedtakDao,
        dokumentDao = dokumentDao,
        producer = producer
    )

    private val sykmeldingDokument = Dokument(UUID.randomUUID(), Dokument.Type.Sykmelding)
    private val søknadDokument = Dokument(UUID.randomUUID(), Dokument.Type.Søknad)
    private val inntektsmeldingDokument = Dokument(UUID.randomUUID(), Dokument.Type.Inntektsmelding)



    @Test
    fun AVVENTER_VILKÅRSPRØVING() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.UnderBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun `avventer historikk fra infotrygd`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun `avventer historikk`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_HISTORIKK),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.UnderBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun `avventer godkjenning`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_GODKJENNING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.UnderBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun `avventer simulering`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_SIMULERING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.UnderBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun `avventer til_utbetaling`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.TIL_UTBETALING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.UnderBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun avsluttet() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVSLUTTET),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.Ferdigbehandlet,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun avsluttet_uten_utbetaling() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.AvsluttetInnenforArbeidsgiverperioden,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun utbetaling_feilet() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.UTBETALING_FEILET),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.UnderBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun AVVENTER_BLOKKERENDE_PERIODE() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_BLOKKERENDE_PERIODE),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    @Test
    fun TIL_INFOTRYGD() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.TIL_INFOTRYGD),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, JsonNode>>()
        verify { producer.send(capture(slot)) }

        assertEquals(
            VedtaksperiodeDto.TilstandDto.ManuellBehandling,
            VedtaksperiodeDto.TilstandDto.valueOf(slot.captured.validertJson()["tilstand"].asText())
        )
    }

    private fun vedtaksperiodeEndret(tilstand: Vedtaksperiode.Tilstand): VedtaksperiodeEndret {
        return VedtaksperiodeEndret(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            vedtaksperiodeId = UUID.randomUUID(),
            hendelseIder = emptyList(),
            timestamp = LocalDateTime.now(),
            tilstand = tilstand
        )
    }

    private fun prepareMock(
        vedtaksperiodeId: UUID,
        dokumenter: List<Dokument>,
        tilstand: Vedtaksperiode.Tilstand
    ) {
        every { vedtaksperiodeDao.finn(vedtaksperiodeId) } returns Vedtaksperiode(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            dokumenter = dokumenter,
            tilstand = tilstand,
            vedtaksperiodeId = vedtaksperiodeId
        )
    }


    private fun sendEvent(
        event: VedtaksperiodeEndret,
        eksisterendeDokumenter: List<Dokument>
    ) {
        prepareMock(
            vedtaksperiodeId = event.vedtaksperiodeId,
            dokumenter = eksisterendeDokumenter,
            tilstand = event.tilstand
        )
        vedtaksperiodeMediator.vedtaksperiodeEndret(event)
    }
}
