package no.nav.helse.sporbar

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class VedtaksperiodeMediator(
    private val vedtaksperiodeDao: VedtaksperiodeDao,
    private val producer: KafkaProducer<String, VedtaksperiodeDto>
) {
    internal fun vedtaksperiodeEndret(vedtaksperiodeEndret: VedtaksperiodeEndret) {
        vedtaksperiodeDao.opprett(
            fnr = vedtaksperiodeEndret.fnr,
            orgnummer = vedtaksperiodeEndret.orgnummer,
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId,
            timestamp = vedtaksperiodeEndret.timestamp,
            hendelseIder = vedtaksperiodeEndret.hendelseIder,
            tilstand = vedtaksperiodeEndret.tilstand
        )

        producer.send(ProducerRecord(
            "topic",
            "fnr",
            vedtaksperiodeDao.finn(vedtaksperiodeEndret.vedtaksperiodeId).toDto()
        ))
    }
}

internal fun Vedtaksperiode.toDto() = VedtaksperiodeDto(
    fnr = fnr,
    orgnummer = orgnummer,
    vedtak = vedtak,
    dokumenter = dokumenter,
    tilstand = oversettTilstand(tilstand)
)

internal fun oversettTilstand(tilstand: Vedtaksperiode.Tilstand) = when (tilstand) {
    Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP -> VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
    else -> VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
}

