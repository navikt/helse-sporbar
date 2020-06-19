package no.nav.helse.sporbar

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val vedtaksperiodeMediator: VedtaksperiodeMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireKey(
                    "@id",
                    "organisasjonsnummer",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "@opprettet",
                    "gjeldendeTilstand"
                )
                it.requireAny("@event_name", listOf("vedtaksperiode_endret"))
                it.requireArray("hendelser")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        try {
            val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
            vedtaksperiodeMediator.vedtaksperiodeEndret(
                VedtaksperiodeEndret(
                    fnr = packet["fødselsnummer"].asText(),
                    orgnummer = packet["organisasjonsnummer"].asText(),
                    vedtaksperiodeId = vedtaksperiodeId,
                    hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) },
                    timestamp = packet["@opprettet"].asLocalDateTime(),
                    tilstand = enumValueOf(packet["gjeldendeTilstand"].asText())
                )
            )
            log.info("Vedtaksperiode $vedtaksperiodeId upserted")
        } catch (e: Exception) {
            sikkerLog.error("Feil ved behandling av melding: {}", packet.toJson(), e)
            throw e
        }
    }
}

internal data class VedtaksperiodeEndret(
    val fnr: String,
    val orgnummer: String,
    val vedtaksperiodeId: UUID,
    val hendelseIder: List<UUID>,
    val timestamp: LocalDateTime,
    val tilstand: Vedtaksperiode.Tilstand
)
