package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog: Logger = LoggerFactory.getLogger("tjenestekall")

internal class NyttDokumentRiver(rapidsConnection: RapidsConnection, private val dokumentDao: DokumentDao) :
    River.PacketListener {
    private companion object {
        private val logg: Logger = LoggerFactory.getLogger(this::class.java)
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny("@event_name", listOf("inntektsmelding", "ny_søknad", "sendt_søknad_nav", "sendt_søknad_arbeidsgiver", "sendt_søknad_arbeidsledig"))
                it.requireKey("@opprettet")
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("inntektsmeldingId") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("id") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("sykmeldingId") { id -> UUID.fromString(id.asText()) }
            }
        }.register(this)
    }
    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.error("Forstod ikke nytt dokument (se sikkerLogg for detaljer)")
        sikkerLogg.error("Forstod ikke nytt dokument: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        try {
            val hendelseId = UUID.fromString(packet["@id"].textValue())
            val opprettet = packet["@opprettet"].asLocalDateTime()

            when (packet["@event_name"].textValue()) {
                "inntektsmelding" -> {
                    val dokumentId = UUID.fromString(packet["inntektsmeldingId"].textValue())
                    dokumentDao.opprett(hendelseId, opprettet, dokumentId, Dokument.Type.Inntektsmelding)
                }
                "ny_søknad", "sendt_søknad_nav", "sendt_søknad_arbeidsgiver", "sendt_søknad_arbeidsledig" -> {
                    val sykmeldingId = UUID.fromString(packet["sykmeldingId"].textValue())
                    dokumentDao.opprett(hendelseId, opprettet, sykmeldingId, Dokument.Type.Sykmelding)
                    val søknadId = UUID.fromString(packet["id"].textValue())
                    dokumentDao.opprett(hendelseId, opprettet, søknadId, Dokument.Type.Søknad)
                }
                else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
            }

            log.info("Dokument med hendelse $hendelseId lagret")
        } catch (e: Exception) {
            sikkerLog.error("Feil ved behandling av dokument: \n${packet.toJson()}", e)
            throw e
        }
    }
}
