package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingUtenUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_uten_utbetaling")
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
                    "type"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }

            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke utbetaling_uten_utbetaling. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke utbetaling_uten_utbetaling:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val utbetalingId = packet["utbetalingId"].let{ UUID.fromString(it.asText())}
        val forbrukteSykedager = packet["forbrukteSykedager"].asInt()
        val gjenståendeSykedager = packet["gjenståendeSykedager"].asInt()
        val type = packet["type"].asText()
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()
        val utbetalingsdager = packet["utbetalingsdager"].toList().map{dag ->
            UtbetalingUtbetalt.UtbetalingdagDto(
                dato = dag["dato"].asLocalDate(),
                type = dag["type"].asText()
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

        utbetalingMediator.utbetalingUtbetalt(UtbetalingUtbetalt(
            event = "utbetaling_uten_utbetaling",
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
            utbetalingsdager = utbetalingsdager
        ))
        log.info("Behandler utbetaling_uten_utbetaling: ${packet["@id"].asText()}")
    }
}
