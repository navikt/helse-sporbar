package no.nav.helse.sporbar

import no.nav.helse.sporbar.Vedtaksperiode.Tilstand
import no.nav.helse.sporbar.VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class VedtaksperiodeMediator(
    private val vedtaksperiodeDao: VedtaksperiodeDao,
    private val vedtakDao: VedtakDao,
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
                Oversetter.oversett(vedtaksperiodeDao.finn(vedtaksperiodeEndret.vedtaksperiodeId))
            )
        )

        log.info("Publiserte vedtaksendring på vedtaksperiode: ${vedtaksperiodeEndret.vedtaksperiodeId}")
    }

    internal fun utbetaling(
        fom: LocalDate,
        tom: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        hendelseIder: List<UUID>,
        vedtak: Vedtak
    ) {

        vedtakDao.opprett(fom, tom, forbrukteSykedager, gjenståendeSykedager, hendelseIder, vedtak)
        producer.send(
            ProducerRecord(
                "topic",
                "fnr",
                Oversetter.oversett(vedtaksperiodeDao.finn(hendelseIder))
            )
        )
        log.info("Publiserte vedtaksendring på vedtaksperiode: id") //TODO: Finn id fra vedtak
    }

    private object Oversetter {
        fun oversett(vedtaksperiode: Vedtaksperiode): VedtaksperiodeDto {
            val situasjon = oversett(vedtaksperiode.tilstand)
            return VedtaksperiodeDto(
                fnr = vedtaksperiode.fnr,
                orgnummer = vedtaksperiode.orgnummer,
                vedtak = vedtaksperiode.vedtak?.let { oversett(vedtaksperiode.vedtak) },
                dokumenter = vedtaksperiode.dokumenter,
                manglendeDokumenter = situasjon.manglendeDokumenter(),
                tilstand = situasjon.tilstandDto
            )
        }

        private fun oversett(vedtak: Vedtak) = VedtakDto(
            fom = vedtak.fom,
            tom = vedtak.tom,
            forbrukteSykedager = vedtak.forbrukteSykedager,
            gjenståendeSykedager = vedtak.gjenståendeSykedager,
            utbetalinger = vedtak.oppdrag.map { oppdrag ->
                VedtakDto.UtbetalingDto(
                    mottaker = oppdrag.mottaker,
                    fagområde = oppdrag.fagområde,
                    totalbeløp = oppdrag.totalbeløp,
                    utbetalingslinjer = oppdrag.utbetalingslinjer.map { linje ->
                        VedtakDto.UtbetalingDto.UtbetalingslinjeDto(
                            fom = linje.fom,
                            tom = linje.tom,
                            dagsats = linje.dagsats,
                            beløp = linje.beløp,
                            grad = linje.grad,
                            sykedager = linje.sykedager
                        )
                    }
                )
            }
        )

        private fun oversett(tilstand: Tilstand) = when (tilstand) {
            Tilstand.TIL_INFOTRYGD -> Situasjon.ManuellBehandling
            Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP -> Situasjon.AvventerSøknadOgInntektsmelding
            Tilstand.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE -> Situasjon.AvventerSøknad
            Tilstand.AVVENTER_GAP -> Situasjon.AvventerInntektsmelding
            Tilstand.AVSLUTTET -> Situasjon.Ferdigbehandlet
            Tilstand.AVSLUTTET_UTEN_UTBETALING -> Situasjon.IngenUtbetaling
            Tilstand.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING -> Situasjon.IngenUtbetaling
            Tilstand.START -> Situasjon.AvventerSøknadOgInntektsmelding //TODO: Dette skal ikke skje
            Tilstand.AVVENTER_SØKNAD_FERDIG_GAP -> Situasjon.AvventerSøknad
            Tilstand.AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD -> Situasjon.IngenUtbetaling
            Tilstand.AVVENTER_INNTEKTSMELDING_FERDIG_GAP -> Situasjon.AvventerInntektsmelding
            Tilstand.MOTTATT_SYKMELDING_UFERDIG_GAP,
            Tilstand.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            Tilstand.AVVENTER_SØKNAD_UFERDIG_GAP,
            Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            Tilstand.AVVENTER_UFERDIG_GAP,
            Tilstand.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            Tilstand.AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            Tilstand.AVVENTER_UFERDIG_FORLENGELSE -> Situasjon.AvventerTidligerePeriode
            Tilstand.AVVENTER_VILKÅRSPRØVING_GAP,
            Tilstand.AVVENTER_HISTORIKK,
            Tilstand.AVVENTER_GODKJENNING,
            Tilstand.AVVENTER_SIMULERING,
            Tilstand.UTBETALING_FEILET,
            Tilstand.TIL_UTBETALING -> Situasjon.UnderBehandling
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

        object AvventerTidligerePeriode : Situasjon(VedtaksperiodeDto.TilstandDto.AvventerTidligerePeriode)
        object UnderBehandling : Situasjon(VedtaksperiodeDto.TilstandDto.UnderBehandling)
        object ManuellBehandling : Situasjon(VedtaksperiodeDto.TilstandDto.ManuellBehandling)
        object Ferdigbehandlet : Situasjon(VedtaksperiodeDto.TilstandDto.Ferdigbehandlet)
        object IngenUtbetaling : Situasjon(VedtaksperiodeDto.TilstandDto.AvsluttetInnenforArbeidsgiverperioden)
    }
}




