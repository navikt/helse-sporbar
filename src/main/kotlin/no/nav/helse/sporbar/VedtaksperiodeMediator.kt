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
        Vedtaksperiode.Tilstand.TIL_INFOTRYGD -> Situasjon.ManuellBehandling
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP -> Situasjon.AvventerSøknadOgInntektsmelding
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE -> Situasjon.AvventerSøknad
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_UFERDIG_GAP -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.AVVENTER_GAP -> Situasjon.AvventerInntektsmelding
        Vedtaksperiode.Tilstand.AVVENTER_HISTORIKK -> Situasjon.UnderBehandling
        Vedtaksperiode.Tilstand.AVVENTER_GODKJENNING -> Situasjon.UnderBehandling
        Vedtaksperiode.Tilstand.AVVENTER_SIMULERING -> Situasjon.UnderBehandling
        Vedtaksperiode.Tilstand.TIL_UTBETALING -> Situasjon.UnderBehandling
        Vedtaksperiode.Tilstand.AVSLUTTET -> Situasjon.Ferdigbehandlet
        Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING -> Situasjon.IngenUtbetaling
        Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING -> Situasjon.IngenUtbetaling
        Vedtaksperiode.Tilstand.UTBETALING_FEILET -> Situasjon.UnderBehandling
        Vedtaksperiode.Tilstand.START -> Situasjon.AvventerSøknadOgInntektsmelding //TODO: Dette skal ikke skje
        Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_FERDIG_GAP -> Situasjon.AvventerSøknad
        Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_UFERDIG_GAP -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING_GAP -> Situasjon.UnderBehandling
        Vedtaksperiode.Tilstand.AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD -> Situasjon.IngenUtbetaling
        Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_FERDIG_GAP -> Situasjon.AvventerInntektsmelding
        Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_GAP -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.AVVENTER_UFERDIG_GAP -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.AVVENTER_SØKNAD_UFERDIG_FORLENGELSE -> Situasjon.AvventerTidligerePeriode
        Vedtaksperiode.Tilstand.AVVENTER_UFERDIG_FORLENGELSE -> Situasjon.AvventerTidligerePeriode
    }
}

private sealed class Situasjon(
    val tilstandDto: VedtaksperiodeDto.TilstandDto
) {

    open fun manglendeDokumenter(): List<Dokument.Type> = emptyList()

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

    object AvventerTidligerePeriode: Situasjon(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode)
    object UnderBehandling: Situasjon(VedtaksperiodeDto.TilstandDto.UnderBehandling)
    object ManuellBehandling : Situasjon(VedtaksperiodeDto.TilstandDto.ManuellBehandling)
    object Ferdigbehandlet: Situasjon(VedtaksperiodeDto.TilstandDto.Ferdigbehandlet)
    object IngenUtbetaling: Situasjon(VedtaksperiodeDto.TilstandDto.AvsluttetInnenforArbeidsgiverperioden)
}

