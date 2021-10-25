package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingUtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_utbetalt")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "automatiskBehandling",
                    "arbeidsgiverOppdrag",
                    "utbetalingsdager",
                    "type",
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("vedtaksperiodeIder")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke utbetaling_utbetalt. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke utbetaling_utbetalt:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val maksdato = packet["maksdato"].asLocalDate()
        val utbetalingId = packet["utbetalingId"].let{ UUID.fromString(it.asText())}
        val forbrukteSykedager = packet["forbrukteSykedager"].asInt()
        val gjenståendeSykedager = packet["gjenståendeSykedager"].asInt()
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()
        val type = packet["type"].asText()
        val utbetalingsdager = packet["utbetalingsdager"].toList().map{dag ->
            UtbetalingUtbetalt.UtbetalingdagDto(
                dato = dag["dato"].asLocalDate(),
                type = dag["type"].asText(),
                begrunnelser = dag.path("begrunnelser").takeUnless(JsonNode::isMissingOrNull)
                    ?.let { mapBegrunnelser(it.toList())} ?: emptyList()
            )
        }

        val arbeidsgiverOppdrag = packet["arbeidsgiverOppdrag"].let{oppdrag ->
            UtbetalingUtbetalt.OppdragDto(
                mottaker = oppdrag["mottaker"].asText(),
                fagområde = oppdrag["fagområde"].asText(),
                fagsystemId = oppdrag["fagsystemId"].asText(),
                nettoBeløp = oppdrag["nettoBeløp"].asInt(),
                utbetalingslinjer = oppdrag["linjer"].map{ linje ->
                    UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto(
                        fom = linje["fom"].asLocalDate(),
                        tom = linje["tom"].asLocalDate(),
                        dagsats = linje["dagsats"].asInt(),
                        totalbeløp = linje["totalbeløp"].asInt(),
                        grad = linje["grad"].asDouble(),
                        stønadsdager = linje["stønadsdager"].asInt()
                    )
                }
            )
        }

        val antallVedtak = packet["vedtaksperiodeIder"].takeIf { it.isArray }?.size()

        utbetalingMediator.utbetalingUtbetalt(UtbetalingUtbetalt(
            event = "utbetaling_utbetalt",
            utbetalingId = utbetalingId,
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            fom = fom,
            tom = tom,
            forbrukteSykedager = forbrukteSykedager,
            gjenståendeSykedager = gjenståendeSykedager,
            automatiskBehandling = automatiskBehandling,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            type = type,
            utbetalingsdager = utbetalingsdager,
            antallVedtak = antallVedtak,
            foreløpigBeregnetSluttPåSykepenger = maksdato
        ))
        log.info("Behandler utbetaling_utbetalt: ${packet["@id"].asText()}")
    }

}

data class UtbetalingUtbetalt(
    val event: String,
    val utbetalingId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto,
    val type: String,
    val utbetalingsdager: List<UtbetalingdagDto>,
    val antallVedtak: Int?,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate
) {
    enum class Begrunnelse {SykepengedagerOppbrukt, MinimumInntekt, EgenmeldingUtenforArbeidsgiverperiode, MinimumSykdomsgrad, ManglerOpptjening, ManglerMedlemskap, EtterDødsdato, Over70, UKJENT }

        data class OppdragDto(
            val mottaker: String,
            val fagområde: String,
            val fagsystemId: String,
            val nettoBeløp: Int,
            val utbetalingslinjer: List<UtbetalingslinjeDto>
        ) {
            data class UtbetalingslinjeDto(
                val fom: LocalDate,
                val tom: LocalDate,
                val dagsats: Int,
                val totalbeløp: Int,
                val grad: Double,
                val stønadsdager: Int
            )
        }
        data class UtbetalingdagDto(
            val dato: LocalDate,
            val type: String,
            val begrunnelser: List<Begrunnelse>
        )
}

internal fun mapBegrunnelser(begrunnelser: List<JsonNode>): List<UtbetalingUtbetalt.Begrunnelse> = begrunnelser.map {
    when (it.asText()) {
        "SykepengedagerOppbrukt" -> UtbetalingUtbetalt.Begrunnelse.SykepengedagerOppbrukt
        "MinimumInntekt" -> UtbetalingUtbetalt.Begrunnelse.MinimumInntekt
        "EgenmeldingUtenforArbeidsgiverperiode" -> UtbetalingUtbetalt.Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode
        "MinimumSykdomsgrad" -> UtbetalingUtbetalt.Begrunnelse.MinimumSykdomsgrad
        "ManglerOpptjening" -> UtbetalingUtbetalt.Begrunnelse.ManglerOpptjening
        "ManglerMedlemskap" -> UtbetalingUtbetalt.Begrunnelse.ManglerMedlemskap
        "EtterDødsdato" -> UtbetalingUtbetalt.Begrunnelse.EtterDødsdato
        "Over70" -> UtbetalingUtbetalt.Begrunnelse.Over70
        else -> {
            log.error("Ukjent begrunnelse $it")
            UtbetalingUtbetalt.Begrunnelse.UKJENT
        }
    }
}
