package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class UtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val dokumentDao: DokumentDao
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
    }

    private fun JsonNode.toDokumenter() =
        dokumentDao.finn(map { UUID.fromString(it.asText()) })

    private fun JsonNode.toOppdrag() = map {
        Vedtak.Oppdrag(
            mottaker = it["mottaker"].asText(),
            fagområde = it["fagområde"].asText(),
            fagsystemId = it["fagsystemId"].asText(),
            totalbeløp = it["totalbeløp"].asInt(),
            utbetalingslinjer = it["utbetalingslinjer"].toUtbetalingslinjer()
        )
    }

    private fun JsonNode.toUtbetalingslinjer() = map {
        Vedtak.Oppdrag.Utbetalingslinje(
            fom = it["fom"].asLocalDate(),
            tom = it["tom"].asLocalDate(),
            dagsats = it["dagsats"].asInt(),
            beløp = it["beløp"].asInt(),
            grad = it["grad"].asDouble(),
            sykedager = it["sykedager"].asInt()
        )
    }
}
