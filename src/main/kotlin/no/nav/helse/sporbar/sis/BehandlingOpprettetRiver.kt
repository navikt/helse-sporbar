package no.nav.helse.sporbar.sis

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sporbar.DokumentDao

interface SisPublisher {
    fun send(melding: String)
}
internal class BehandlingOpprettetRiver(rapid: RapidsConnection, private val dokumentDao: DokumentDao, private val sisPublisher: SisPublisher) {

}
