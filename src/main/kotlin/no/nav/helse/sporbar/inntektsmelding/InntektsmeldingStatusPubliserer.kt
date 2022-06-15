package no.nav.helse.sporbar.inntektsmelding

import java.time.Duration
import java.time.LocalDateTime
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class InntektsmeldingStatusPubliserer(
    rapidsConnection: RapidsConnection,
    private val inntektsmeldingStatusMediator: InntektsmeldingStatusMediator,
    private val publiseringintervall: Duration = Duration.ofSeconds(30),
    private val statustimeout: Duration = Duration.ofMinutes(5)
): River.PacketListener {

    init {
        require(publiseringintervall > Duration.ZERO) { "publiseringsintervall kan ikke være 0." }
        require(statustimeout.toSeconds() > 0) { "statustimeout må være minst 1 sekund." }
        River(rapidsConnection).register(this)
    }

    private var sistPublisert = LocalDateTime.MIN
    private fun skalPublisereNå() = sistPublisert < LocalDateTime.now().minus(publiseringintervall)
    private fun harPublisertNå() { sistPublisert = LocalDateTime.now() }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (!skalPublisereNå()) return
        inntektsmeldingStatusMediator.publiser(statustimeout)
        harPublisertNå()
    }
}