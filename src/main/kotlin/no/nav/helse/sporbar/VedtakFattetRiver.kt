package no.nav.helse.sporbar

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetMediator: VedtakFattetMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtak_fattet")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "hendelser",
                    "sykepengegrunnlag",
                    "grunnlagForSykepengegrunnlag",
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver",
                    "begrensning",
                    "inntekt"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                it.require("vedtakFattetTidspunkt", JsonNode::asLocalDateTime)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.interestedIn("utbetalingId") { id -> UUID.fromString(id.asText()) }

            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke vedtak_fattet. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) }
        val inntekt = packet["inntekt"].asDouble()
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble()
        val grunnlagForSykepengegrunnlag = packet["grunnlagForSykepengegrunnlag"].asDouble()
        val grunnlagForSykepengegrunnlagPerArbeidsgiver = objectMapper.readValue(packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString(), object : TypeReference<Map<String, Double>>() {})
        val begrensning = packet["begrensning"].asText()
        val vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asLocalDateTime()

        val utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let {
            UUID.fromString(it.asText())
        }

        vedtakFattetMediator.vedtakFattet(
            VedtakFattet(
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = skjæringstidspunkt,
                hendelseIder = hendelseIder,
                inntekt = inntekt,
                sykepengegrunnlag = sykepengegrunnlag,
                grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
                grunnlagForSykepengegrunnlagPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver,
                begrensning = begrensning,
                utbetalingId = utbetalingId,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt
            )
        )
        log.info("Behandler vedtakFattet: ${packet["@id"].asText()}")
        sikkerLog.info("Behandler vedtakFattet: ${packet["@id"].asText()}")
    }
}

internal data class VedtakFattet(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val hendelseIder: List<UUID>,
    val inntekt: Double,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>,
    val begrensning: String,
    val utbetalingId: UUID?,
    val vedtakFattetTidspunkt: LocalDateTime
)
