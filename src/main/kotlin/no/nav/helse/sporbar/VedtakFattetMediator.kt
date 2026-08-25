package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.spedisjon.HentMeldingerResponse
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.sporbar.dto.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetMediator(
    private val spedisjonClient: SpedisjonClient,
    private val producer: KafkaProducer<String, String>,
    private val spForsikringClient: SpForsikringClient,
    private val sendTilSis: Boolean = (System.getenv("NAIS_CLUSTER_NAME") ?: "false") == "dev-gcp",
) {
    internal fun vedtakFattet(vedtakFattet: VedtakFattet) {
        val callId = UUID.randomUUID().toString()
        sikkerLogg.info("Henter dokumenter {}", kv("callId", callId))
        log.info("Henter dokumenter for {}", kv("callId", callId))

        val dokumenter: HentMeldingerResponse =
            retryBlocking {
                spedisjonClient.hentMeldinger(vedtakFattet.hendelseIder, callId).getOrThrow()
            }
        val forsikringsvurdering =
            vedtakFattet.forsikringsvurderingId?.let { forsikringsvurderingId ->
                retryBlocking {
                    spForsikringClient.hentForsikringsvurdering(forsikringsvurderingId, callId)
                }
            }
        sendVedtakFattetPåTbdVedtak(vedtakFattet, dokumenter, forsikringsvurdering)
        sendVedtakFattetPåTbdSisForHag(vedtakFattet, dokumenter)
    }

    internal fun sendVedtakFattetPåTbdSisForHag(
        vedtakFattet: VedtakFattet,
        dokumenter: HentMeldingerResponse,
    ) {

        if (sendTilSis) {
            if (vedtakFattet.yrkesaktivitetstype == "ARBEIDSTAKER") {
                val eksternDto = oversettVedtakFattetForHag(vedtakFattet, dokumenter.tilAlleDokumenter())
                val meldingForEkstern = objectMapper.writeValueAsString(eksternDto)
                producer.send(
                    ProducerRecord(
                        "tbd.sis",
                        null,
                        vedtakFattet.fødselsnummer,
                        meldingForEkstern,
                        listOf(VedtakType.VedtakFattet.header()),
                    ),
                )
                sikkerLogg.info("Publiserer vedtakFattet til sistopicet: {}", meldingForEkstern)
            }
        }
    }

    private fun sendVedtakFattetPåTbdVedtak(
        vedtakFattet: VedtakFattet,
        dokumenter: HentMeldingerResponse,
        forsikringsvurdering: Forsikringsvurdering?,
    ) {
        val eksternDto = oversett(vedtakFattet, dokumenter.tilSøknadsdokumenter(), forsikringsvurdering)
        val meldingForEkstern = objectMapper.writeValueAsString(eksternDto)
        producer.send(
            ProducerRecord(
                "tbd.vedtak",
                null,
                vedtakFattet.fødselsnummer,
                meldingForEkstern,
                listOf(VedtakType.VedtakFattet.header()),
            ),
        )
        sikkerLogg.info("Publiserer vedtakFattet {}", meldingForEkstern)
        log.info("Publiserte vedtakFattet for {}", dokumenter.tilSøknadsdokumenter().map { it.dokumentId })
    }

    private fun oversett(
        vedtakFattet: VedtakFattet,
        dokumenter: List<Dokument>,
        forsikringsvurdering: Forsikringsvurdering?,
    ): VedtakFattetForEksternDto =
        VedtakFattetForEksternDto(
            fødselsnummer = vedtakFattet.fødselsnummer,
            aktørId = vedtakFattet.aktørId,
            organisasjonsnummer = vedtakFattet.organisasjonsnummer,
            yrkesaktivitetstype = vedtakFattet.yrkesaktivitetstype,
            fom = vedtakFattet.fom,
            tom = vedtakFattet.tom,
            skjæringstidspunkt = vedtakFattet.skjæringstidspunkt,
            sykepengegrunnlag = vedtakFattet.sykepengegrunnlag,
            dokumenter =
                dokumenter.map {
                    DokumentForEksternDto(
                        it.dokumentId,
                        when (it.type) {
                            Dokument.Type.Sykmelding -> DokumentForEksternDto.Type.Sykmelding
                            Dokument.Type.Søknad -> DokumentForEksternDto.Type.Søknad
                            Dokument.Type.Inntektsmelding -> DokumentForEksternDto.Type.Inntektsmelding
                        },
                    )
                },
            utbetalingId = vedtakFattet.utbetalingId,
            vedtakFattetTidspunkt = vedtakFattet.vedtakFattetTidspunkt,
            sykepengegrunnlagsfakta = oversett(vedtakFattet.sykepengegrunnlagsfakta),
            begrunnelser =
                vedtakFattet.begrunnelser.map { begrunnelse ->
                    BegrunnelseForEksternDto(
                        begrunnelse.type,
                        begrunnelse.begrunnelse,
                        begrunnelse.perioder.map {
                            PeriodeForEksternDto(it.fom, it.tom)
                        },
                    )
                },
            tags = vedtakFattet.tags,
            saksbehandler =
                vedtakFattet.saksbehandlerNavnOgIdent?.let {
                    NavnOgIdentForEksternDto(
                        navn = it.navn,
                        ident = it.ident,
                    )
                },
            beslutter =
                vedtakFattet.beslutterNavnOgIdent?.let {
                    NavnOgIdentForEksternDto(
                        navn = it.navn,
                        ident = it.ident,
                    )
                },
            forsikringsvurderingId = vedtakFattet.forsikringsvurderingId,
            forsikringsvurdering =
                forsikringsvurdering?.let {
                    ForsikringsvurderingForEksternDto(
                        individuellForsikringNavn = it.individuellForsikringNavn,
                        kollektivForsikringNavn = it.kollektivForsikringNavn,
                        dekning =
                            it.dekning?.let { dekning ->
                                DekningForEksternDto(
                                    grad = dekning.grad,
                                    fraDag = dekning.fraDag,
                                )
                            },
                    )
                },
        )

    private fun oversett(sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta) =
        when (sykepengegrunnlagsfakta) {
            is FastsattEtterHovedregel ->
                FastsattEtterHovedregelForEksternDto(
                    fastsatt = sykepengegrunnlagsfakta.fastsatt,
                    omregnetÅrsinntekt = sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                    innrapportertÅrsinntekt = sykepengegrunnlagsfakta.innrapportertÅrsinntekt,
                    avviksprosent = sykepengegrunnlagsfakta.avviksprosent,
                    `6G` = sykepengegrunnlagsfakta.`6G`,
                    tags = sykepengegrunnlagsfakta.tags,
                    arbeidsgivere =
                        sykepengegrunnlagsfakta.arbeidsgivere.map {
                            FastsattEtterHovedregelForEksternDto.Arbeidsgiver(
                                it.arbeidsgiver,
                                it.omregnetÅrsinntekt,
                            )
                        },
                )

            is FastsattEtterSkjønn ->
                FastsattEtterSkjønnForEksternDto(
                    fastsatt = sykepengegrunnlagsfakta.fastsatt,
                    omregnetÅrsinntekt = sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                    innrapportertÅrsinntekt = sykepengegrunnlagsfakta.innrapportertÅrsinntekt,
                    skjønnsfastsatt = sykepengegrunnlagsfakta.skjønnsfastsatt,
                    avviksprosent = sykepengegrunnlagsfakta.avviksprosent,
                    `6G` = sykepengegrunnlagsfakta.`6G`,
                    tags = sykepengegrunnlagsfakta.tags,
                    arbeidsgivere =
                        sykepengegrunnlagsfakta.arbeidsgivere.map {
                            FastsattEtterSkjønnForEksternDto.Arbeidsgiver(
                                it.arbeidsgiver,
                                it.omregnetÅrsinntekt,
                                it.skjønnsfastsatt,
                            )
                        },
                )

            is FastsattIInfotrygd ->
                FastsattIInfotrygdForEksternDto(
                    fastsatt = sykepengegrunnlagsfakta.fastsatt,
                    omregnetÅrsinntekt = sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                )

            is SykepengegrunnlagsfaktaSelvstendigNæringsdrivende ->
                SykepengegrunnlagsfaktaSelvstendigDto(
                    fastsatt = sykepengegrunnlagsfakta.fastsatt,
                    `6G` = sykepengegrunnlagsfakta.`6G`,
                    tags = sykepengegrunnlagsfakta.tags,
                    selvstendig =
                        SykepengegrunnlagsfaktaSelvstendigDto.Selvstendig(
                            beregningsgrunnlag = sykepengegrunnlagsfakta.selvstendig.beregningsgrunnlag,
                            pensjonsgivendeInntekter =
                                sykepengegrunnlagsfakta.selvstendig.pensjonsgivendeInntekter.map {
                                    SykepengegrunnlagsfaktaSelvstendigDto.Selvstendig.PensjonsgivendeInntekt(
                                        årstall = it.årstall,
                                        beløp = it.beløp,
                                    )
                                },
                        ),
                )
        }

    private fun oversettVedtakFattetForHag(
        vedtakFattet: VedtakFattet,
        dokumenter: List<Dokument>,
    ): VedtakFattetForHagDto =
        VedtakFattetForHagDto(
            fødselsnummer = vedtakFattet.fødselsnummer,
            organisasjonsnummer = vedtakFattet.organisasjonsnummer,
            yrkesaktivitetstype = vedtakFattet.yrkesaktivitetstype,
            vedtaksperiodeId = vedtakFattet.vedtaksperiodeId,
            fom = vedtakFattet.fom,
            tom = vedtakFattet.tom,
            skjæringstidspunkt = vedtakFattet.skjæringstidspunkt,
            dokumenter =
                dokumenter.map {
                    DokumentForEksternDto(
                        it.dokumentId,
                        when (it.type) {
                            Dokument.Type.Sykmelding -> DokumentForEksternDto.Type.Sykmelding
                            Dokument.Type.Søknad -> DokumentForEksternDto.Type.Søknad
                            Dokument.Type.Inntektsmelding -> DokumentForEksternDto.Type.Inntektsmelding
                        },
                    )
                },
            sykepengegrunnlag = vedtakFattet.sykepengegrunnlag,
            utbetalingsdager =
                vedtakFattet.utbetalingsdager.map {
                    UtbetalingsdagDto(
                        dato = it.dato,
                        type = it.type,
                        sykdomsgrad = it.sykdomsgrad,
                        dekningsgrad = it.dekningsgrad,
                        beløpTilBruker = it.beløpTilBruker,
                        beløpTilArbeidsgiver = it.beløpTilArbeidsgiver,
                        begrunnelser = it.begrunnelser,
                    )
                },
            vedtakFattetTidspunkt = vedtakFattet.vedtakFattetTidspunkt,
            vedtaksUtfallTilArbeidsgiver = VedtaksUtfall.INNVILGELSE, // Foreløpig bare hardkodet må snakkes med fag om hvordan dette skal gjøres
            saksbehandlerIdent = vedtakFattet.saksbehandlerNavnOgIdent?.ident,
            saksbehandlerNavn = vedtakFattet.saksbehandlerNavnOgIdent?.navn,
            beslutterIdent = vedtakFattet.beslutterNavnOgIdent?.ident,
            beslutterNavn = vedtakFattet.beslutterNavnOgIdent?.navn,
            automatiskFattet = vedtakFattet.automatiskFattet,
            harArbeidsgiverØnsketRefusjon = vedtakFattet.tags.contains("ArbeidsgiverØnskerRefusjon"),
        )
}
