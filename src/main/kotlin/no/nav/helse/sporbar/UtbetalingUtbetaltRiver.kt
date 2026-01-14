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
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import no.nav.helse.sporbar.dto.BegrunnelseDto
import no.nav.helse.sporbar.dto.OppdragDto.Companion.parseOppdrag
import no.nav.helse.sporbar.dto.UtbetalingUtbetaltDto
import no.nav.helse.sporbar.dto.UtbetalingdagDto

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

val NULLE_UT_TOMME_OPPDRAG = System.getenv("NULLE_UT_TOMME_OPPDRAG")?.toBoolean() ?: false

internal class UtbetalingUtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "utbetaling_utbetalt") }
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
                    "type",
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
                    requireKey("type", "beløpTilArbeidsgiver", "beløpTilBruker", "sykdomsgrad")
                    interestedIn("begrunnelser")
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("forstod ikke utbetaling_utbetalt. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke utbetaling_utbetalt:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val callId = packet["@id"].asText()
        withMDC("callId" to callId) {
            håndterUtbetalingUtbetalt(packet, callId)
        }
    }

    private fun håndterUtbetalingUtbetalt(packet: JsonMessage, callId: String) {
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
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()
        val type = packet["type"].asText()
        val utbetalingsdager = mapUtbetaligsdager(packet["utbetalingsdager"])
        val arbeidsgiverOppdrag = parseOppdrag(packet["arbeidsgiverOppdrag"])
        val personOppdrag = parseOppdrag(packet["personOppdrag"])

        utbetalingMediator.utbetalingUtbetalt(
            UtbetalingUtbetaltDto(
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
        log.info("Behandler utbetaling_utbetalt: ${packet["@id"].asText()}")
        sikkerLog.info("Behandler utbetaling_utbetalt: ${packet["@id"].asText()}")
    }
}

internal fun mapUtbetaligsdager(utbetalingsdager: JsonNode) = utbetalingsdager.map { utbetalingsdag ->
    UtbetalingdagDto(
        dato = utbetalingsdag["dato"].asLocalDate(),
        type = utbetalingsdag["type"].dagtype,
        beløpTilArbeidsgiver = utbetalingsdag["beløpTilArbeidsgiver"].asInt(),
        beløpTilSykmeldt = utbetalingsdag["beløpTilBruker"].asInt(),
        sykdomsgrad = utbetalingsdag["sykdomsgrad"].asInt(),
        begrunnelser = utbetalingsdag.path("begrunnelser")
            .takeUnless(JsonNode::isMissingOrNull)
            ?.map { it.begrunnelse }
            ?: emptyList()
    )
}

private val KjenteDagtyper = setOf(
    "ArbeidsgiverperiodeDag",
    "NavDag",
    "NavHelgDag",
    "Arbeidsdag",
    "Fridag",
    "AvvistDag",
    "UkjentDag",
    "ForeldetDag",
    "Permisjonsdag",
    "Feriedag",
    "ArbeidIkkeGjenopptattDag",
    "AndreYtelser",
    "Ventetidsdag"
)

private val JsonNode.dagtype get(): String {
    val fraSpleis = asText()
    if (fraSpleis in KjenteDagtyper) return fraSpleis
    throw IllegalStateException("Ny dagtype fra Spleis: $fraSpleis. Vurder om denne skal eksponeres videre ut på tbd.utbetaling")
}

private val JsonNode.begrunnelse get() = when (val tekstverdi = asText()) {
    "SykepengedagerOppbrukt" -> BegrunnelseDto.SykepengedagerOppbrukt
    "SykepengedagerOppbruktOver67" -> BegrunnelseDto.SykepengedagerOppbruktOver67
    "MinimumInntekt" -> BegrunnelseDto.MinimumInntekt
    "MinimumInntektOver67" -> BegrunnelseDto.MinimumInntektOver67
    "EgenmeldingUtenforArbeidsgiverperiode" -> BegrunnelseDto.EgenmeldingUtenforArbeidsgiverperiode
    "AndreYtelserAap" -> BegrunnelseDto.AndreYtelserAap
    "AndreYtelserDagpenger" -> BegrunnelseDto.AndreYtelserDagpenger
    "AndreYtelserForeldrepenger" -> BegrunnelseDto.AndreYtelserForeldrepenger
    "AndreYtelserOmsorgspenger" -> BegrunnelseDto.AndreYtelserOmsorgspenger
    "AndreYtelserOpplaringspenger" -> BegrunnelseDto.AndreYtelserOpplaringspenger
    "AndreYtelserPleiepenger" -> BegrunnelseDto.AndreYtelserPleiepenger
    "AndreYtelserSvangerskapspenger" -> BegrunnelseDto.AndreYtelserSvangerskapspenger
    "MinimumSykdomsgrad" -> BegrunnelseDto.MinimumSykdomsgrad
    "ManglerOpptjening" -> BegrunnelseDto.ManglerOpptjening
    "ManglerMedlemskap" -> BegrunnelseDto.ManglerMedlemskap
    "EtterDødsdato" -> BegrunnelseDto.EtterDødsdato
    "Over70" -> BegrunnelseDto.Over70
    else -> error("Ukjent begrunnelse $tekstverdi")
}
