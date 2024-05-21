package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import java.time.ZoneId
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ANNEN_PERIODE
import org.slf4j.LoggerFactory

internal class VedtaksperiodeVenterPåSøknadEllerInntektsmeldingForAnnenPeriodeRiver(rapid: RapidsConnection, private val sisPublisher: SisPublisher) :
    River.PacketListener {

    init {
        River(rapid).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_venter")
                it.demandAny("venterPå.venteårsak.hva", listOf("SØKNAD", "INNTEKTSMELDING"))
                it.requireKey("vedtaksperiodeId", "behandlingId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                // Venter på en annen periode som i sin tur venter på noe annet enn godkjenning
                it.demand("venterPå.vedtaksperiodeId") { id ->
                    check(id.asText() != it["vedtaksperiodeId"].asText())
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke vedtaksperiode_venter pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke vedtaksperiode_venter pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val behandlingId = packet["behandlingId"].asText().toUUID()
        val tidspunkt = packet["@opprettet"].asLocalDateTime().atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime()
        sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingstatus(vedtaksperiodeId, behandlingId, tidspunkt, VENTER_PÅ_ANNEN_PERIODE))
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(VedtaksperiodeVenterPåSøknadEllerInntektsmeldingForAnnenPeriodeRiver::class.java)
    }
}
