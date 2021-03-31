package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class UtbetalingUtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetaling_utbetalt")
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
        ))
        log.info("Behandler utbetaling_utbetalt: ${packet["@id"].asText()}")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        super.onError(problems, context)
    }

    override fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {
        super.onSevere(error, context)
    }
}

data class UtbetalingUtbetalt(
    val utbetalingId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto
) {
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
}
