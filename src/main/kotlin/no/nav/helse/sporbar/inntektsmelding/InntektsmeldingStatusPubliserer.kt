package no.nav.helse.sporbar.inntektsmelding

import java.time.Duration
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class InntektsmeldingStatusPubliserer(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator,
    private val statustimeout: Duration = Duration.ofMinutes(5)
): River.PacketListener {

    init {
        require(statustimeout.toSeconds() > 0) { "statustimeout må være minst 1 sekund." }
        River(rapidsConnection)
            .validate { it.demandAny("@event_name", listOf("minutt", "publiser_im_status")) }
            .register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        inntektsmeldingStatusMediator.publiser(statustimeout)
    }
}