package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class UtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakDao: VedtakDao
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
                    "gjenståendeSykedager"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        vedtakDao.opprett(
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            forbrukteSykedager = packet["forbrukteSykedager"].asInt(),
            gjenståendeSykedager = packet["gjenståendeSykedager"].asInt(),
            hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) },
            vedtak = Vedtak(
                fom = packet["fom"].asLocalDate(),
                tom = packet["tom"].asLocalDate(),
                forbrukteSykedager = packet["forbrukteSykedager"].asInt(),
                gjenståendeSykedager = packet["gjenståendeSykedager"].asInt(),
                oppdrag = packet["utbetalt"].map { oppdrag ->
                    Vedtak.Oppdrag(
                        mottaker = oppdrag["mottaker"].asText(),
                        fagområde = oppdrag["fagområde"].asText(),
                        fagsystemId = oppdrag["fagsystemId"].asText(),
                        totalbeløp = oppdrag["totalbeløp"].asInt(),
                        utbetalingslinjer = oppdrag["utbetalingslinjer"].map { utbetalingslinje ->
                            Vedtak.Oppdrag.Utbetalingslinje(
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
