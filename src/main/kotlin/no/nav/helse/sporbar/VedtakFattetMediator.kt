package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
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
    private val producer: KafkaProducer<String, String>
) {
    internal fun vedtakFattet(vedtakFattet: VedtakFattet) {
        val callId = UUID.randomUUID().toString()
        sikkerLogg.info("Henter dokumenter {}", kv("callId", callId))
        log.info("Henter dokumenter for {}", kv("callId", callId))

        val dokumenter: List<Dokument> = retryBlocking {
            spedisjonClient.hentMeldinger(vedtakFattet.hendelseIder, callId).getOrThrow().tilDokumenter()
        }
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


