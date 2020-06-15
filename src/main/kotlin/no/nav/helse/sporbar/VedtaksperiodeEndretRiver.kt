package no.nav.helse.sporbar

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val vedtaksperiodeDao: VedtaksperiodeDao,
    private val vedtaksperiodeMediator: VedtaksperiodeMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireKey("@id", "organisasjonsnummer", "fødselsnummer", "vedtaksperiodeId", "@opprettet", "gjeldendeTilstand")
                it.requireAny("@event_name", listOf("vedtaksperiode_endret"))
                it.requireArray("hendelser")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        vedtaksperiodeDao.opprett(
            fnr = packet["fødselsnummer"].asText(),
            orgnummer = packet["organisasjonsnummer"].asText(),
            vedtaksperiodeId = vedtaksperiodeId,
            hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) },
            timestamp = packet["@opprettet"].asLocalDateTime(),
            tilstand = enumValueOf(packet["gjeldendeTilstand"].asText())
        )
        log.info("Vedtaksperiode $vedtaksperiodeId upserted")
    }
}
