package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetMediator(
    private val dokumentDao: DokumentDao,
    private val producer: KafkaProducer<String, JsonNode>
) {
    internal fun vedtakFattet(vedtakFattet: VedtakFattet) {

        val dokumenter: List<Dokument> = dokumentDao.finn(vedtakFattet.hendelseIder)
        val meldingForEkstern = objectMapper.valueToTree<JsonNode>(oversett(vedtakFattet, dokumenter))

        producer.send(
            ProducerRecord(
                "tbd.vedtak",
                null,
                vedtakFattet.fødselsnummer,
                meldingForEkstern
            )
        )
        sikkerLogg.info("Publiserer {}", meldingForEkstern)
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
            utbetalingId = vedtakFattet.utbetalingId
        )
    }
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
    val utbetalingId: UUID?
)




