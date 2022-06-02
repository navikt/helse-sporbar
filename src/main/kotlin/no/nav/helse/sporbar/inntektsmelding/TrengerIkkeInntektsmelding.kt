package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class TrengerIkkeInntektsmelding(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingDao: InntektsmeldingDao
) : River.PacketListener {

    private val log: Logger = LoggerFactory.getLogger("sporbar")

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "trenger_ikke_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "aktørId", "vedtaksperiodeId")
                it.requireArray("søknadIder")
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)

            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Trenger ikke inntektsmelding for vedtaksperiode ${packet["vedtaksperiodeId"].asText()}")
        inntektsmeldingDao.trengerIkkeInntektsmelding(packet.somMelding(trengerInntektsmelding = false))
    }
}

internal fun JsonMessage.somMelding(trengerInntektsmelding: Boolean): InntektsmeldingPakke {
    val id = UUID.randomUUID()
    val hendelseId = UUID.fromString(this["@id"].asText())
    val fødselsnummer = this["fødselsnummer"].asText()
    val aktørId = this["aktørId"].asText()
    val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
    val organisasjonsnummer = this["organisasjonsnummer"].asText()
    val fom = this["fom"].asLocalDate()
    val tom = this["tom"].asLocalDate()
    val opprettet = this["@opprettet"].asLocalDateTime()

    return InntektsmeldingPakke(
        id = id,
        hendelseId = hendelseId,
        fødselsnummer = fødselsnummer,
        aktørId = aktørId,
        organisasjonsnummer = organisasjonsnummer,
        vedtaksperiodeId = vedtaksperiodeId,
        trengerInntektsmelding = trengerInntektsmelding,
        fom = fom,
        tom = tom,
        opprettet = opprettet,
        json = this
    )
}
data class InntektsmeldingPakke(
    val id: UUID,
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val vedtaksperiodeId: UUID,
    val trengerInntektsmelding: Boolean,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime,
    val json: JsonMessage
)
