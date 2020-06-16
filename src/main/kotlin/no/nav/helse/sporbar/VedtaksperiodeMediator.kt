package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)


internal class VedtaksperiodeMediator(
    private val vedtaksperiodeDao: VedtaksperiodeDao,
    private val producer: KafkaProducer<String, VedtaksperiodeDto>
) {
    fun vedtaksperiodeEndret(vedtaksperiodeEndret: VedtaksperiodeEndret) {
        vedtaksperiodeDao.opprett(
            fnr = vedtaksperiodeEndret.fnr,
            orgnummer = vedtaksperiodeEndret.orgnummer,
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId,
            timestamp = vedtaksperiodeEndret.timestamp,
            hendelseIder = vedtaksperiodeEndret.hendelseIder,
            tilstand = vedtaksperiodeEndret.tilstand
        )

        val dto = vedtaksperiodeDao.finn(vedtaksperiodeEndret.vedtaksperiodeId).toDto()
        producer.send(ProducerRecord("topic", "fnr", dto))
    }
}

internal fun Vedtaksperiode.toDto() = VedtaksperiodeDto(
    fnr = fnr,
    orgnummer = orgnummer,
    vedtak = vedtak,
    dokumenter = dokumenter,
    tilstand = oversettTilstand(tilstand)
)

internal fun oversettTilstand(tilstand: Vedtaksperiode.Tilstand) = when(tilstand) {
    Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP -> VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
    else -> VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
}

