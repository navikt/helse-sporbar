package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sporbar.Vedtaksperiode.Tilstand
import no.nav.helse.sporbar.VedtaksperiodeDto.TilstandDto.AvventerDokumentasjon
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeMediator(
    private val vedtaksperiodeDao: VedtaksperiodeDao,
    private val vedtakDao: VedtakDao,
    private val dokumentDao: DokumentDao,
    private val producer: KafkaProducer<String, JsonNode>
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

        val maybeVedtaksperiode = vedtaksperiodeDao.finn(vedtaksperiodeEndret.vedtaksperiodeId)
        if (maybeVedtaksperiode == null) {
            log.warn("Fant ikke vedtaksperiode:${vedtaksperiodeEndret.vedtaksperiodeId} vi fikk oppdatering på.")
            return
        }

        producer.send(
            ProducerRecord(
                "aapen-helse-sporbar",
                null,
                vedtaksperiodeEndret.fnr,
                objectMapper.valueToTree(Oversetter.oversett(maybeVedtaksperiode)),
                listOf(RecordHeader("type", Meldingstype.Behandlingstilstand.name.toByteArray()))
            )
        )

        log.info("Publiserte vedtaksendring på vedtaksperiode: ${vedtaksperiodeEndret.vedtaksperiodeId}")
    }

    internal fun utbetaling(utbetaling: Utbetaling, fødselsnummer: String) {
        vedtakDao.opprett(utbetaling)
        val dokumenter = dokumentDao.finn(utbetaling.hendelseIder)
        val vedtak = objectMapper.valueToTree<JsonNode>(Oversetter.oversett(utbetaling, dokumenter))
        producer.send(
            ProducerRecord(
                "aapen-helse-sporbar",
                null,
                fødselsnummer,
                vedtak,
                listOf(RecordHeader("type", Meldingstype.Vedtak.name.toByteArray()))
            )
        )

        sikkerLogg.info("Publiserer {}", keyValue("vedtak", vedtak))
        log.info("Publiserte utbetalinger for {}", keyValue("dokumenter", dokumenter.map { it.dokumentId }))
    }

    private object Oversetter {
        fun oversett(vedtaksperiode: Vedtaksperiode): VedtaksperiodeDto {
            val situasjon = oversett(vedtaksperiode.tilstand)
            return VedtaksperiodeDto(
                fnr = vedtaksperiode.fnr,
                orgnummer = vedtaksperiode.orgnummer,
                dokumenter = vedtaksperiode.dokumenter,
                manglendeDokumenter = situasjon.manglendeDokumenter(),
                tilstand = situasjon.tilstandDto
            )
        }

        fun oversett(utbetaling: Utbetaling, dokumenter: List<Dokument>) = VedtakDto(
            fom = utbetaling.fom,
            tom = utbetaling.tom,
            forbrukteSykedager = utbetaling.forbrukteSykedager,
            gjenståendeSykedager = utbetaling.gjenståendeSykedager,
            automatiskBehandling = utbetaling.automatiskBehandling,
            sykepengegrunnlag = utbetaling.sykepengegrunnlag,
            utbetalinger = utbetaling.oppdrag.map { oppdrag ->
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
            },
            dokumenter = dokumenter
        )

        private fun oversett(tilstand: Tilstand) = when (tilstand) {
            Tilstand.TIL_INFOTRYGD -> Situasjon.ManuellBehandling
            Tilstand.MOTTATT_SYKMELDING_FERDIG_GAP -> Situasjon.AvventerSøknadOgInntektsmelding
            Tilstand.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE -> Situasjon.AvventerSøknad
            Tilstand.AVVENTER_GAP -> Situasjon.AvventerInntektsmelding
            Tilstand.AVSLUTTET -> Situasjon.Ferdigbehandlet
            Tilstand.AVSLUTTET_UTEN_UTBETALING -> Situasjon.IngenUtbetaling
            Tilstand.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING -> Situasjon.IngenUtbetaling
            Tilstand.START -> error("Ingen vedtaksperiodeEndret-event vi ha START som gjeldende tilstand")
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




