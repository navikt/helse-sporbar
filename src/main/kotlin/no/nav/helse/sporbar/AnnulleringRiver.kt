package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sporbar.dto.AnnulleringDto
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog: Logger = LoggerFactory.getLogger("tjenestekall")

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val aivenProducer: KafkaProducer<String, String>,
    private val speedClient: SpeedClient
):
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "utbetaling_annullert") }
            validate {
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "organisasjonsnummer",
                    "tidspunkt",
                    "fom",
                    "tom",
                    "utbetalingId",
                    "korrelasjonsId"
                )
                it.interestedIn("arbeidsgiverFagsystemId", "personFagsystemId")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLog.error("forstod ikke utbetaling_annullert: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val callId = packet["@id"].asText()
        withMDC("callId" to callId) {
            håndterAnnullering(packet, callId)
        }
    }

    private fun håndterAnnullering(packet: JsonMessage, callId: String) {
        val ident = packet["fødselsnummer"].asText()
        val identer = retryBlocking { speedClient.hentFødselsnummerOgAktørId(ident, callId).getOrThrow() }

        val annulleringDto = AnnulleringDto(
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            fødselsnummer = identer.fødselsnummer,
            tidsstempel = packet["tidspunkt"].asLocalDateTime(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            utbetalingId = UUID.fromString(packet["utbetalingId"].asText()),
            korrelasjonsId = UUID.fromString(packet["korrelasjonsId"].asText()),
            arbeidsgiverFagsystemId = packet["arbeidsgiverFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText(),
            personFagsystemId = packet["personFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()
        )
        val annulleringJson = objectMapper.writeValueAsString(annulleringDto)
        aivenProducer.send(ProducerRecord("tbd.utbetaling", null, identer.fødselsnummer, annulleringJson, listOf(UtbetalingType.Annullering.header())))
        log.info("Publiserte annullering")
        sikkerLog.info("Publiserte annullering $annulleringJson")
    }
}
