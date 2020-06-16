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
import java.util.*

private const val FNR = ""
private const val ORGNUMMER = ""

internal class VedtaksperiodeMediatorTest {

    private val vedtaksperiodeDao = mockk<VedtaksperiodeDao>(relaxed = true)
    private val producer = mockk<KafkaProducer<String, VedtaksperiodeDto>>(relaxed = true)
    private val vedtaksperiodeMediator = VedtaksperiodeMediator(vedtaksperiodeDao, producer)

    private val sykmeldingDokument = Dokument(UUID.randomUUID(), Dokument.Type.Sykmelding)

    @Test
    fun `sendt sykmelding uten forlengelse`() {
        every { vedtaksperiodeDao.finn(any()) } returns Vedtaksperiode(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            vedtak = null,
            dokumenter = listOf(sykmeldingDokument),
            tilstand = Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP
        )

        vedtaksperiodeMediator.vedtaksperiodeEndret(VedtaksperiodeEndret(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            vedtaksperiodeId = UUID.randomUUID(),
            hendelseIder = emptyList(),
            timestamp = LocalDateTime.now(),
            tilstand = Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP
        ))

        val slot = CapturingSlot<ProducerRecord<String,VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
    }

    @Test
    fun `sendt sykmelding med forlengelse`() {
        every { vedtaksperiodeDao.finn(any()) } returns Vedtaksperiode(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            vedtak = null,
            dokumenter = listOf(sykmeldingDokument),
            tilstand = Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP
        )

        vedtaksperiodeMediator.vedtaksperiodeEndret(VedtaksperiodeEndret(
            fnr = FNR,
            orgnummer = ORGNUMMER,
            vedtaksperiodeId = UUID.randomUUID(),
            hendelseIder = emptyList(),
            timestamp = LocalDateTime.now(),
            tilstand = Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE
        ))

        val slot = CapturingSlot<ProducerRecord<String,VedtaksperiodeDto>>()
        verify { producer.send(capture(slot)) }

        assertEquals(VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon, slot.captured.value().tilstand)
        assertEquals(1, slot.captured.value().manglendeDokumenter.size)
    }
}
