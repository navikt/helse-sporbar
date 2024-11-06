package no.nav.helse.sporbar

import no.nav.helse.sporbar.dto.BegrunnelseForEksternDto
import no.nav.helse.sporbar.dto.DokumentForEkstern
import no.nav.helse.sporbar.dto.FastsattEtterHovedregelForEksternDto
import no.nav.helse.sporbar.dto.FastsattEtterSkjønnForEksternDto
import no.nav.helse.sporbar.dto.FastsattIInfotrygdForEksternDto
import no.nav.helse.sporbar.dto.PeriodeForEksternDto
import no.nav.helse.sporbar.dto.VedtakFattetForEksternDto
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetMediator(
    private val dokumentDao: DokumentDao,
    private val producer: KafkaProducer<String, String>
) {
    internal fun vedtakFattet(vedtakFattet: VedtakFattet) {
        val dokumenter: List<Dokument> = dokumentDao.finn(vedtakFattet.hendelseIder)
        val eksternDto = oversett(vedtakFattet, dokumenter)
        val meldingForEkstern = objectMapper.writeValueAsString(eksternDto)
        producer.send(ProducerRecord("tbd.vedtak", null, vedtakFattet.fødselsnummer, meldingForEkstern, listOf(Meldingstype.VedtakFattet.header())))
        sikkerLogg.info("Publiserer vedtakFattet {}", meldingForEkstern)
        log.info("Publiserte vedtakFattet for {}", dokumenter.map { it.dokumentId })
    }

    private fun oversett(vedtakFattet: VedtakFattet, dokumenter: List<Dokument>): VedtakFattetForEksternDto {
        return VedtakFattetForEksternDto(
            fødselsnummer = vedtakFattet.fødselsnummer,
            aktørId = vedtakFattet.aktørId,
            organisasjonsnummer = vedtakFattet.organisasjonsnummer,
            fom = vedtakFattet.fom,
            tom = vedtakFattet.tom,
            skjæringstidspunkt = vedtakFattet.skjæringstidspunkt,
            inntekt = vedtakFattet.inntekt,
            sykepengegrunnlag = vedtakFattet.sykepengegrunnlag,
            grunnlagForSykepengegrunnlag = vedtakFattet.grunnlagForSykepengegrunnlag,
            grunnlagForSykepengegrunnlagPerArbeidsgiver = vedtakFattet.grunnlagForSykepengegrunnlagPerArbeidsgiver,
            begrensning = vedtakFattet.begrensning,
            dokumenter = dokumenter.map {
                DokumentForEkstern(it.dokumentId, when (it.type) {
                    Dokument.Type.Sykmelding -> DokumentForEkstern.Type.Sykmelding
                    Dokument.Type.Søknad -> DokumentForEkstern.Type.Søknad
                    Dokument.Type.Inntektsmelding -> DokumentForEkstern.Type.Inntektsmelding
                })
            },
            utbetalingId = vedtakFattet.utbetalingId,
            vedtakFattetTidspunkt = vedtakFattet.vedtakFattetTidspunkt,
            sykepengegrunnlagsfakta = vedtakFattet.sykepengegrunnlagsfakta?.let { oversett(it) },
            begrunnelser = vedtakFattet.begrunnelser.map { begrunnelse ->
                BegrunnelseForEksternDto(
                    begrunnelse.type,
                    begrunnelse.begrunnelse,
                    begrunnelse.perioder.map {
                        PeriodeForEksternDto(it.fom, it.tom)
                    }
                )
            },
            tags = vedtakFattet.tags
        )
    }

    private fun oversett(sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta) = when (sykepengegrunnlagsfakta) {
        is FastsattEtterHovedregel -> FastsattEtterHovedregelForEksternDto(
            fastsatt = sykepengegrunnlagsfakta.fastsatt,
            omregnetÅrsinntekt = sykepengegrunnlagsfakta.omregnetÅrsinntekt,
            innrapportertÅrsinntekt = sykepengegrunnlagsfakta.innrapportertÅrsinntekt,
            avviksprosent = sykepengegrunnlagsfakta.avviksprosent,
            `6G`= sykepengegrunnlagsfakta.`6G`,
            tags = sykepengegrunnlagsfakta.tags,
            arbeidsgivere = sykepengegrunnlagsfakta.arbeidsgivere.map { FastsattEtterHovedregelForEksternDto.Arbeidsgiver(it.arbeidsgiver, it.omregnetÅrsinntekt) }
        )
        is FastsattEtterSkjønn -> FastsattEtterSkjønnForEksternDto(
            fastsatt = sykepengegrunnlagsfakta.fastsatt,
            omregnetÅrsinntekt = sykepengegrunnlagsfakta.omregnetÅrsinntekt,
            innrapportertÅrsinntekt = sykepengegrunnlagsfakta.innrapportertÅrsinntekt,
            skjønnsfastsatt = sykepengegrunnlagsfakta.skjønnsfastsatt,
            avviksprosent = sykepengegrunnlagsfakta.avviksprosent,
            `6G`= sykepengegrunnlagsfakta.`6G`,
            tags = sykepengegrunnlagsfakta.tags,
            arbeidsgivere = sykepengegrunnlagsfakta.arbeidsgivere.map { FastsattEtterSkjønnForEksternDto.Arbeidsgiver(it.arbeidsgiver, it.omregnetÅrsinntekt, it.skjønnsfastsatt) }
        )
        is FastsattIInfotrygd -> FastsattIInfotrygdForEksternDto(
            fastsatt = sykepengegrunnlagsfakta.fastsatt,
            omregnetÅrsinntekt = sykepengegrunnlagsfakta.omregnetÅrsinntekt
        )
    }
}


