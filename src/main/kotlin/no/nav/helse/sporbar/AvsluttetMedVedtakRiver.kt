package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.UUID
import no.nav.helse.sporbar.dto.AvsluttetMedVedtakDto
import no.nav.helse.sporbar.dto.Utbetalingsdag
import no.nav.helse.sporbar.dto.sykepengegrunnlagsfaktaAvsluttetMedVedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class AvsluttetMedVedtakRiver(
    rapidsConnection: RapidsConnection,
    private val avsluttetMedVedtakMediator: AvsluttetMedVedtakMediator,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "avsluttet_med_vedtak")
            }
            validate {
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "organisasjonsnummer",
                    "vedtaksperiodeId",
                    "behandlingId",
                    "fom",
                    "tom",
                    "skjæringstidspunkt",
                    "sykepengegrunnlag",
                    "vedtakFattetTidspunkt",
                    "sykepengegrunnlagsfakta",
                    "automatiskBehandling",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "foreløpigBeregnetSluttPåSykepenger"
                )
                it.requireArray("hendelser")
                it.requireArray("utbetalingsdager")

                it.interestedIn("yrkesaktivitetstype")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Forstod ikke avsluttet_med_vedtak. (se sikkerlogg for melding)")
        sikkerLog.error("Forstod ikke avsluttet_med_vedtak:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val callId = packet["@id"].asText()
        withMDC("callId" to callId) {
            håndterAvsluttetMedVedtak(packet)
        }
    }

    private fun håndterAvsluttetMedVedtak(packet: JsonMessage) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val yrkesaktivitetstype = packet["yrkesaktivitetstype"].asText()
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        val behandlingId = UUID.fromString(packet["behandlingId"].asText())
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) }
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble()
        val vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asLocalDateTime()
        val sykepengegrunnlagsfakta = packet["sykepengegrunnlagsfakta"].sykepengegrunnlagsfaktaAvsluttetMedVedtak()
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()
        val forbrukteSykedager = packet["forbrukteSykedager"].asInt()
        val gjenståendeSykedager = packet["gjenståendeSykedager"].asInt()
        val foreløpigBeregnetSluttPåSykepenger = packet["foreløpigBeregnetSluttPåSykepenger"].asLocalDate()

        val utbetalingsdager = packet["utbetalingsdager"].map {
            Utbetalingsdag(
                dato = it["dato"].asLocalDate(),
                type = it["type"].asText(),
                beløpTilArbeidsgiver = it["beløpTilArbeidsgiver"].asInt(),
                beløpTilBruker = it["beløpTilBruker"].asInt(),
                sykdomsgrad = it["sykdomsgrad"].asInt(),
                begrunnelser = it["begrunnelser"].takeUnless(JsonNode::isMissingOrNull)?.map { begrunnelse ->
                    begrunnelse.asText()
                }
            )
        }
        avsluttetMedVedtakMediator.avsluttetMedVedtak(
            AvsluttetMedVedtakDto(
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
                yrkesaktivitetstype = yrkesaktivitetstype,
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                fom = fom,
                tom = tom,
                hendelseIder = hendelseIder,
                skjæringstidspunkt = skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                sykepengegrunnlagsfakta = sykepengegrunnlagsfakta,
                automatiskBehandling = automatiskBehandling,
                forbrukteSykedager = forbrukteSykedager,
                gjenståendeSykedager = gjenståendeSykedager,
                foreløpigBeregnetSluttPåSykepenger = foreløpigBeregnetSluttPåSykepenger,
                utbetalingsdager = utbetalingsdager,
            )
        )
    }
}

