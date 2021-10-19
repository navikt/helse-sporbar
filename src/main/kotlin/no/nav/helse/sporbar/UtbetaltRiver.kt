package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class UtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val vedtaksperiodeMediator: VedtaksperiodeMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "hendelser",
                    "utbetalt",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "automatiskBehandling",
                    "sykepengegrunnlag",
                    "månedsinntekt",
                    "maksdato"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        vedtaksperiodeMediator.utbetaling(
            fødselsnummer = packet["fødselsnummer"].asText(),
            utbetaling = Utbetaling(
                organisasjonsnummer = packet["organisasjonsnummer"].asText(),
                fom = packet["fom"].asLocalDate(),
                tom = packet["tom"].asLocalDate(),
                forbrukteSykedager = packet["forbrukteSykedager"].asInt(),
                gjenståendeSykedager = packet["gjenståendeSykedager"].asInt(),
                maksdato = packet["maksdato"].asLocalDate(),
                sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
                månedsinntekt = packet["månedsinntekt"].asDouble(),
                automatiskBehandling = packet["automatiskBehandling"].asBoolean(),
                hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) },
                oppdrag = packet["utbetalt"].map { oppdrag ->
                    Utbetaling.Oppdrag(
                        mottaker = oppdrag["mottaker"].asText(),
                        fagområde = oppdrag["fagområde"].asText(),
                        fagsystemId = oppdrag["fagsystemId"].asText(),
                        totalbeløp = oppdrag["totalbeløp"].asInt(),
                        utbetalingslinjer = oppdrag["utbetalingslinjer"].map { utbetalingslinje ->
                            Utbetaling.Oppdrag.Utbetalingslinje(
                                fom = utbetalingslinje["fom"].asLocalDate(),
                                tom = utbetalingslinje["tom"].asLocalDate(),
                                dagsats = utbetalingslinje["dagsats"].asInt(),
                                beløp = utbetalingslinje["beløp"].asInt(),
                                grad = utbetalingslinje["grad"].asDouble(),
                                sykedager = utbetalingslinje["sykedager"].asInt()
                            )
                        }
                    )
                }
            )
        )

        log.info("Lagrer vedtak på vedtaksperiode fra utbetalthendelse: ${packet["@id"].asText()}")
    }
}
