package no.nav.helse.sporbar

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

private const val FNR = ""
private const val ORGNUMMER = ""

internal class VedtaksperiodeMediatorTest {

    private val vedtaksperiodeDao = mockk<VedtaksperiodeDao>(relaxed = true)
    private val producer = mockk<KafkaProducer<String, VedtaksperiodeDto>>(relaxed = true)
    private val vedtaksperiodeMediator = VedtaksperiodeMediator(vedtaksperiodeDao, producer)

    private val sykmeldingDokument = Dokument(UUID.randomUUID(), Dokument.Type.Sykmelding)
    private val søknadDokument = Dokument(UUID.randomUUID(), Dokument.Type.Søknad)
    private val inntektsmeldingDokument = Dokument(UUID.randomUUID(), Dokument.Type.Inntektsmelding)

    @Test
    fun `sendt sykmelding uten forlengelse`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }
        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
        assertEquals(2, slot.captured.value().manglendeDokumenter.size)
    }

    @Test
    fun `sendt sykmelding med forlengelse`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE),
            eksisterendeDokumenter = listOf(sykmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
        assertEquals(1, slot.captured.value().manglendeDokumenter.size)
    }

    @Test
    fun `sendt sykmelding uten forlengelse påfølgende uferdig behandling`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_UFERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `sendt sykmelding forlengelse påfølgende uferdig behandling`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE),
            eksisterendeDokumenter = listOf(sykmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_SØKNAD_FERDIG_GAP`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_FERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_SØKNAD_UFERDIG_GAP`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_UFERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_VILKÅRSPRØVING_GAP`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.UnderBehandling, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvsluttetInnenforArbeidsgiverperioden, slot.captured.value().tilstand)
    }

    @Test
    fun `avventer kort historikk fra infotrygd`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
    }

    @Test
    fun `avventer historikk`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_HISTORIKK),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.UnderBehandling, slot.captured.value().tilstand)
    }

    @Test
    fun `avventer godkjenning`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_GODKJENNING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.UnderBehandling, slot.captured.value().tilstand)
    }

    @Test
    fun `avventer simulering`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_SIMULERING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.UnderBehandling, slot.captured.value().tilstand)
    }

    @Test
    fun `avventer til_utbetaling`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.TIL_UTBETALING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.UnderBehandling, slot.captured.value().tilstand)
    }

    @Test
    fun `avsluttet`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVSLUTTET),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.Ferdigbehandlet, slot.captured.value().tilstand)
    }

    @Test
    fun `avsluttet_uten_utbetaling`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvsluttetInnenforArbeidsgiverperioden, slot.captured.value().tilstand)
    }

    @Test
    fun `avsluttet_uten_utbetaling_med_inntektsmelding`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvsluttetInnenforArbeidsgiverperioden, slot.captured.value().tilstand)
    }

    @Test
    fun `utbetaling_feilet`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.UTBETALING_FEILET),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.UnderBehandling, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_INNTEKTSMELDING_FERDIG_GAP`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_FERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_UFERDIG_GAP`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_INNTEKTSMELDING_UFERDIG_GAP`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_GAP),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_SØKNAD_UFERDIG_FORLENGELSE`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_UFERDIG_FORLENGELSE),
            eksisterendeDokumenter = listOf(sykmeldingDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `AVVENTER_UFERDIG_FORLENGELSE`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.AVVENTER_UFERDIG_FORLENGELSE),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode, slot.captured.value().tilstand)
    }

    @Test
    fun `TIL_INFOTRYGD`() {
        sendEvent(
            event = vedtaksperiodeEndret(Vedtaksperiode.Tilstand.TIL_INFOTRYGD),
            eksisterendeDokumenter = listOf(sykmeldingDokument, søknadDokument, inntektsmeldingDokument)
        )

        val slot = CapturingSlot<ProducerRecord<String, VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.ManuellBehandling, slot.captured.value().tilstand)
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
        tilstand: Vedtaksperiode.Tilstand,
        vedtak: Vedtak? = null
    ) {
        every { vedtaksperiodeDao.finn(vedtaksperiodeId) } returns Vedtaksperiode(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            vedtak = vedtak,
            dokumenter = dokumenter,
            tilstand = tilstand
        )
    }



    fun sendEvent(
        event: VedtaksperiodeEndret,
        eksisterendeDokumenter: List<Dokument>,
        eksisterendeVedtak: Vedtak? = null
    ) {
        prepareMock(
            vedtaksperiodeId = event.vedtaksperiodeId,
            dokumenter = eksisterendeDokumenter,
            tilstand = event.tilstand,
            vedtak = eksisterendeVedtak
        )
        vedtaksperiodeMediator.vedtaksperiodeEndret(event)
    }
}
