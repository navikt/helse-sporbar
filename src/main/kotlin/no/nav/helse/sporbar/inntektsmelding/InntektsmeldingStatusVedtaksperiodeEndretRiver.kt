package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.sporbar.Vedtaksperiode.Tilstand
import no.nav.helse.sporbar.Vedtaksperiode.Tilstand.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.sporbar.Vedtaksperiode.Tilstand.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.sporbar.inntektsmelding.TrengerIkkeInntektsmeldingRiver.Companion.somHarInntektsmelding
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class InntektsmeldingStatusVedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator
): River.PacketListener {

    private val log: Logger = LoggerFactory.getLogger("inntektsmeldingstatus")

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "vedtaksperiode_endret")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "aktørId", "vedtaksperiodeId", "hendelser")
                it.interestedIn("hendelser")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireAny("gjeldendeTilstand", listOf(AVSLUTTET_UTEN_UTBETALING.name, AVVENTER_BLOKKERENDE_PERIODE.name))
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        when (val tilstand = enumValueOf<Tilstand>(packet["gjeldendeTilstand"].asText())) {
            AVSLUTTET_UTEN_UTBETALING -> {
                log.info("Vedtaksperiode endret til AVSLUTTET_UTEN_UTBETALING. Trenger ikke inntektsmelding. {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
                inntektsmeldingStatusMediator.lagre(packet.somTrengerIkkeInntektsmelding())
            }
            AVVENTER_BLOKKERENDE_PERIODE -> {
                log.info("Vedtaksperiode endret til AVVENTER_BLOKKERENDE_PERIODE. Har inntektsmelding. {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
                inntektsmeldingStatusMediator.lagre(packet.somHarInntektsmelding())
            }
            else -> {
                log.warn("Vedtaksperiode endret til $tilstand. Forventet ikke dette i forbindelse med inntektsmeldingstatus. {}", keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()))
            }
        }
    }

    private fun JsonMessage.somTrengerIkkeInntektsmelding(): TrengerIkkeInntektsmelding {
        val id = UUID.randomUUID()
        val hendelseId = UUID.fromString(this["@id"].asText())
        val fødselsnummer = this["fødselsnummer"].asText()
        val aktørId = this["aktørId"].asText()
        val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
        val organisasjonsnummer = this["organisasjonsnummer"].asText()
        val fom = this["fom"].asLocalDate()
        val tom = this["tom"].asLocalDate()
        val opprettet = this["@opprettet"].asLocalDateTime()

        return TrengerIkkeInntektsmelding(
            id = id,
            hendelseId = hendelseId,
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            fom = fom,
            tom = tom,
            opprettet = opprettet,
            json = this
        )
    }
}
