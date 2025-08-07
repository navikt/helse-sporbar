package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asInstant
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetSelvstendigNæringsdrivendeRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetMediator: VedtakFattetMediator,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtak_fattet")
                it.requireValue("yrkesaktivitetstype", "SELVSTENDIG")
            }
            validate {
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "sykepengegrunnlag",
                    "sykepengegrunnlagsfakta"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                it.require("vedtakFattetTidspunkt", JsonNode::asInstant)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("begrunnelser")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("forstod ikke vedtak_fattet. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val callId = packet["@id"].asText()
        withMDC("callId" to callId) {
            håndterVedtakFattet(packet, callId)
        }
    }

    private fun håndterVedtakFattet(packet: JsonMessage, callId: String) {
        val ident = packet["fødselsnummer"].asText()
        val identer = retryBlocking { speedClient.hentFødselsnummerOgAktørId(ident, callId).getOrThrow() }

        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val sykepengegrunnlag = BigDecimal(packet["sykepengegrunnlag"].asText())
        val sykepengegrunnlagsfakta = packet["sykepengegrunnlagsfakta"].run {
            SykepengegrunnlagsfaktaSelvstendigNæringsdrivende(
                beregningsgrunnlag = BigDecimal(get("beregningsgrunnlag").asText()),
                pensjonsgivendeInntekter = get("pensjonsgivendeInntekter").map {
                    PensjonsgivendeInntekter(
                        år = it.get("år").asInt(),
                        inntekt = BigDecimal(it.get("inntekt").asText()),
                    )
                },
                erBegrensetTil6G = get("erBegrensetTil6G").asBoolean(),
                `6G` = BigDecimal(get("6G").asText()),
            )
        }
        val vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asInstant()
        val begrunnelser = packet["begrunnelser"]
            .takeUnless(JsonNode::isMissingOrNull)
            ?.map { begrunnelse ->
                Begrunnelse(
                    begrunnelse["type"].asText(),
                    begrunnelse["begrunnelse"].asText(),
                    begrunnelse["perioder"].map {
                        Periode(it["fom"].asLocalDate(), it["tom"].asLocalDate())
                    }
                )
            } ?: emptyList()
        val utbetalingId = UUID.fromString(packet["utbetalingId"].asText())

        vedtakFattetMediator.vedtakFattet(
            VedtakFattetSelvstendigNæringsdrivende(
                fødselsnummer = identer.fødselsnummer,
                aktørId = identer.aktørId,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag,
                utbetalingId = utbetalingId,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                sykepengegrunnlagsfakta = sykepengegrunnlagsfakta,
                begrunnelser = begrunnelser,
            )
        )
        log.info("Behandler vedtakFattet: ${packet["@id"].asText()}")
        sikkerLog.info("Behandler vedtakFattet: ${packet["@id"].asText()}")
    }
}

internal data class VedtakFattetSelvstendigNæringsdrivende(
    val fødselsnummer: String,
    val aktørId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val sykepengegrunnlag: BigDecimal,
    val utbetalingId: UUID,
    val vedtakFattetTidspunkt: Instant,
    val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaSelvstendigNæringsdrivende,
    val begrunnelser: List<Begrunnelse>,
)

data class SykepengegrunnlagsfaktaSelvstendigNæringsdrivende(
    val beregningsgrunnlag: BigDecimal,
    val pensjonsgivendeInntekter: List<PensjonsgivendeInntekter>,
    val erBegrensetTil6G: Boolean,
    val `6G`: BigDecimal,
)

data class PensjonsgivendeInntekter(
    val år: Int,
    val inntekt: BigDecimal,
)
