package no.nav.helse.sporbar

import no.nav.helse.sporbar.VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
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

        producer.send(
            ProducerRecord(
                "topic",
                "fnr",
                MapperThing().oversett(vedtaksperiodeDao.finn(vedtaksperiodeEndret.vedtaksperiodeId))
            )
        )
    }
}

internal class MapperThing {
    fun oversett(vedtaksperiode: Vedtaksperiode): VedtaksperiodeDto {
        val situasjon = oversettTilstand(vedtaksperiode.tilstand)
        return VedtaksperiodeDto(
            fnr = vedtaksperiode.fnr,
            orgnummer = vedtaksperiode.orgnummer,
            vedtak = vedtaksperiode.vedtak,
            dokumenter = vedtaksperiode.dokumenter,
            manglendeDokumenter = situasjon.manglendeDokumenter(),
            tilstand = situasjon.tilstandDto
        )
    }

    private fun oversettTilstand(tilstand: Vedtaksperiode.Tilstand) = when (tilstand) {
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP -> Situasjon.AvventerSøknadOgInntektsmelding
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE -> Situasjon.AvventerSøknad
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_UFERDIG_GAP -> Situasjon.AvventerSøknadOgInntektsmelding
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE -> Situasjon.AvventerSøknad
        Vedtaksperiode.Tilstand.AVVENTER_GAP -> Situasjon.AvventerInntektsmelding
        Vedtaksperiode.Tilstand.AVVENTER_HISTORIKK -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_GODKJENNING -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_SIMULERING -> TODO()
        Vedtaksperiode.Tilstand.TIL_UTBETALING -> TODO()
        Vedtaksperiode.Tilstand.TIL_INFOTRYGD -> TODO()
        Vedtaksperiode.Tilstand.AVSLUTTET -> TODO()
        Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING -> TODO()
        Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING -> TODO()
        Vedtaksperiode.Tilstand.UTBETALING_FEILET -> TODO()
        Vedtaksperiode.Tilstand.START -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_FERDIG_GAP -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_UFERDIG_GAP -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING_GAP -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_FERDIG_GAP -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_GAP -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_UFERDIG_GAP -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_UFERDIG_FORLENGELSE -> TODO()
        Vedtaksperiode.Tilstand.AVVENTER_UFERDIG_FORLENGELSE -> TODO()
    }
}

private sealed class Situasjon(val tilstandDto: VedtaksperiodeDto.TilstandDto) {

    abstract fun manglendeDokumenter(): List<Dokument.Type>

    object AvventerSøknad : Situasjon(AvventerDokumentasjon) {
        override fun manglendeDokumenter(): List<Dokument.Type> = listOf(Dokument.Type.Søknad)
    }

    object AvventerSøknadOgInntektsmelding : Situasjon(AvventerDokumentasjon) {
        override fun manglendeDokumenter(): List<Dokument.Type> =
            listOf(Dokument.Type.Søknad, Dokument.Type.Inntektsmelding)
    }

    object AvventerInntektsmelding : Situasjon(AvventerDokumentasjon) {
        override fun manglendeDokumenter(): List<Dokument.Type> = listOf(Dokument.Type.Inntektsmelding)
    }
}

