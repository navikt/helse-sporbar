package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class UtbetalingUtenUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetaling_uten_utbetaling")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "automatiskBehandling",
                    "arbeidsgiverOppdrag"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }

            }
        }.register(this)
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
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()

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
            utbetalingId = utbetalingId,
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            fom = fom,
            tom = tom,
            forbrukteSykedager = forbrukteSykedager,
            gjenståendeSykedager = gjenståendeSykedager,
            automatiskBehandling = automatiskBehandling,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag
        ), eventName = "utbetaling_uten_utbetaling")
        log.info("Behandler utbetaling_uten_utbetaling: ${packet["@id"].asText()}")
    }

}
