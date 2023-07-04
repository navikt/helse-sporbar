package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetMediator(
    private val dokumentDao: DokumentDao,
    private val producer: KafkaProducer<String, JsonNode>,
    private val utvidetFormat: Boolean = false
) {
    internal fun vedtakFattet(vedtakFattet: VedtakFattet) {

        val dokumenter: List<Dokument> = dokumentDao.finn(vedtakFattet.hendelseIder)
        val meldingForEkstern = objectMapper.valueToTree<ObjectNode>(oversett(vedtakFattet, dokumenter)).let { ekstern ->
            if (!utvidetFormat) ekstern
            else  {
                val sykepengegrunnlagsfakta = vedtakFattet.sykepengegrunnlagsfakta
                if (sykepengegrunnlagsfakta == null) ekstern.medNyeDefaultfelter.putNull("sykepengegrunnlagsfakta")
                else ekstern.medNyeDefaultfelter.set("sykepengegrunnlagsfakta", objectMapper.valueToTree(oversett(sykepengegrunnlagsfakta)))
            }
        }

        producer.send(
            ProducerRecord(
                "tbd.vedtak",
                null,
                vedtakFattet.fødselsnummer,
                meldingForEkstern
            )
        )
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
            dokumenter = dokumenter,
            utbetalingId = vedtakFattet.utbetalingId,
            vedtakFattetTidspunkt = vedtakFattet.vedtakFattetTidspunkt
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

    private val ObjectNode.medNyeDefaultfelter get() = put("versjon", "1.1.0").apply { putArray("begrunnelser") }
}

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val dokumenter: List<Dokument>,
    val inntekt: Double,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>,
    val begrensning: String,
    val utbetalingId: UUID?,
    val vedtakFattetTidspunkt: LocalDateTime
)

sealed class SykepengegrunnlagsfaktaForEksternDto

data class FastsattEtterHovedregelForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
    val innrapportertÅrsinntekt: Double,
    val avviksprosent: Double,
    val `6G`: Double,
    val tags: Set<String>,
    val arbeidsgivere: List<Arbeidsgiver>
): SykepengegrunnlagsfaktaForEksternDto() {
    data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double)
}

data class FastsattEtterSkjønnForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
    val innrapportertÅrsinntekt: Double,
    val skjønnsfastsatt: Double,
    val avviksprosent: Double,
    val `6G`: Double,
    val tags: Set<String>,
    val arbeidsgivere: List<Arbeidsgiver>
): SykepengegrunnlagsfaktaForEksternDto() {
    data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val skjønnsfastsatt: Double)
}

data class FastsattIInfotrygdForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
) : SykepengegrunnlagsfaktaForEksternDto()



