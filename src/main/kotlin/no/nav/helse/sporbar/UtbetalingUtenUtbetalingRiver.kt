package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import no.nav.helse.sporbar.dto.OppdragDto.Companion.parseOppdrag
import no.nav.helse.sporbar.dto.UtbetalingUtenUtbetalingDto

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingUtenUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "utbetaling_uten_utbetaling") }
            validate {
                it.requireKey(
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "stønadsdager",
                    "automatiskBehandling",
                    "arbeidsgiverOppdrag",
                    "personOppdrag",
                    "type"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
                it.require("korrelasjonsId") { id -> UUID.fromString(id.asText()) }

                it.requireKey("arbeidsgiverOppdrag.mottaker", "arbeidsgiverOppdrag.fagområde", "arbeidsgiverOppdrag.fagsystemId",
                    "arbeidsgiverOppdrag.nettoBeløp", "arbeidsgiverOppdrag.stønadsdager")
                it.require("arbeidsgiverOppdrag.fom", JsonNode::asLocalDate)
                it.require("arbeidsgiverOppdrag.tom", JsonNode::asLocalDate)
                it.requireArray("arbeidsgiverOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("sats", "totalbeløp", "grad", "stønadsdager")
                }
                it.requireKey("personOppdrag.mottaker", "personOppdrag.fagområde", "personOppdrag.fagsystemId",
                    "personOppdrag.nettoBeløp", "personOppdrag.stønadsdager")
                it.require("personOppdrag.fom", JsonNode::asLocalDate)
                it.require("personOppdrag.tom", JsonNode::asLocalDate)
                it.requireArray("personOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("sats", "totalbeløp", "grad", "stønadsdager")
                }
                it.requireArray("utbetalingsdager") {
                    require("dato", JsonNode::asLocalDate)
                    requireKey("type")
                    interestedIn("begrunnelser")
                    interestedIn("beløpTilArbeidsgiver", "beløpTilBruker", "sykdomsgrad")
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("forstod ikke utbetaling_uten_utbetaling. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke utbetaling_uten_utbetaling:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val callId = packet["@id"].asText()
        withMDC("callId" to callId) {
            håndterUtbetalingUtenUtbetaling(packet, callId)
        }
    }

    private fun håndterUtbetalingUtenUtbetaling(packet: JsonMessage, callId: String) {
        val ident = packet["fødselsnummer"].asText()
        val identer = retryBlocking { speedClient.hentFødselsnummerOgAktørId(ident, callId).getOrThrow() }

        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val maksdato = packet["maksdato"].asLocalDate()
        val utbetalingId = packet["utbetalingId"].let{ UUID.fromString(it.asText())}
        val korrelasjonsId = packet["korrelasjonsId"].let{ UUID.fromString(it.asText())}
        val forbrukteSykedager = packet["forbrukteSykedager"].asInt()
        val gjenståendeSykedager = packet["gjenståendeSykedager"].asInt()
        val stønadsdager = packet["stønadsdager"].asInt()
        val type = packet["type"].asText()
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()
        val utbetalingsdager = mapUtbetaligsdager(packet["utbetalingsdager"])
        val arbeidsgiverOppdrag = parseOppdrag(packet["arbeidsgiverOppdrag"])
        val personOppdrag = parseOppdrag(packet["personOppdrag"])

        utbetalingMediator.utbetalingUtenUtbetaling(
            UtbetalingUtenUtbetalingDto(
                utbetalingId = utbetalingId,
                korrelasjonsId = korrelasjonsId,
                fødselsnummer = identer.fødselsnummer,
                aktørId = identer.aktørId,
                organisasjonsnummer = organisasjonsnummer,
                fom = fom,
                tom = tom,
                forbrukteSykedager = forbrukteSykedager,
                gjenståendeSykedager = gjenståendeSykedager,
                stønadsdager = stønadsdager,
                automatiskBehandling = automatiskBehandling,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                type = type,
                utbetalingsdager = utbetalingsdager,
                foreløpigBeregnetSluttPåSykepenger = maksdato
            )
        )
        log.info("Behandler utbetaling_uten_utbetaling: ${packet["@id"].asText()}")
        sikkerLog.info("Behandler utbetaling_uten_utbetaling: ${packet["@id"].asText()}")
    }
}
