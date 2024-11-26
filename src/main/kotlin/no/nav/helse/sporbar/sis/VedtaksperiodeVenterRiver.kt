package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.sporbar.objectMapper
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ANNEN_PERIODE
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.asOffsetDateTime
import no.nav.helse.sporbar.sis.VedtaksperiodeVenterRiver.Venteårsak.*
import no.nav.helse.sporbar.tilSøknader
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

internal class VedtaksperiodeVenterRiver(rapid: RapidsConnection, private val spedisjonClient: SpedisjonClient, private val sisPublisher: SisPublisher, ) : River.PacketListener {

    init {
        River(rapid).apply {
            precondition {
                it.requireValue("@event_name", "vedtaksperioder_venter")
            }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
            validate {
                it.requireArray("vedtaksperioder") {
                    requireKey("vedtaksperiodeId", "behandlingId", "organisasjonsnummer", "venterPå.vedtaksperiodeId", "venterPå.organisasjonsnummer", "hendelser")
                }
            }
        }.register(this)

        // todo: denne riveren er deprecated
        River(rapid).apply {
            precondition {
                it.requireValue("@event_name", "vedtaksperiode_venter")
                it.requireAny("venterPå.venteårsak.hva", listOf("SØKNAD", "INNTEKTSMELDING", "GODKJENNING"))
            }
            validate {
                it.requireKey("vedtaksperiodeId", "behandlingId", "organisasjonsnummer", "venterPå.vedtaksperiodeId", "venterPå.organisasjonsnummer", "hendelser")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(object : River.PacketListener {
            override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
                logg.info("Håndterer ikke vedtaksperiode_venter pga. problem: se sikker logg")
                sikkerlogg.info("Håndterer ikke vedtaksperiode_venter pga. problem: {}", problems.toExtendedReport())
            }

            override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
                val opprettet = packet["@opprettet"].asOffsetDateTime()
                try {
                    håndterVedtaksperiodeVenter(opprettet, objectMapper.readValue<VedtaksperiodeVenterDto>(packet.toJson()))
                } catch (err: Exception) {
                    sikkerlogg.error("Kunne ikke tolke vedtaksperiode venter: ${err.message}", err)
                }
            }
        })
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.info("Håndterer ikke vedtaksperioder_venter pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke vedtaksperioder_venter pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val opprettet = packet["@opprettet"].asOffsetDateTime()
        packet["vedtaksperioder"].forEach { venter ->
            try {
                håndterVedtaksperiodeVenter(opprettet, objectMapper.convertValue<VedtaksperiodeVenterDto>(venter))
            } catch (err: Exception) {
                sikkerlogg.error("Kunne ikke tolke vedtaksperiode venter: ${err.message}", err)
            }
        }
    }

    private fun håndterVedtaksperiodeVenter(opprettet: OffsetDateTime, node: VedtaksperiodeVenterDto) {
        if (node.venterPå.venteårsak.hva !in listOf("SØKNAD", "INNTEKTSMELDING", "GODKJENNING")) return

        val callId = UUID.randomUUID().toString()
        logg.info("Henter dokumenter {}", kv("callId", callId))
        sikkerlogg.info("Henter dokumenter {}", kv("callId", callId))
        val eksterneSøknadIder = retryBlocking {
            spedisjonClient.hentMeldinger(node.hendelser.toList(), callId).getOrThrow().tilSøknader()
        } ?: return sikkerlogg.error("Nå kom det en vedtaksperiode_venter uten at vi fant eksterne søknadIder. Er ikke dét rart?")

        val vedtaksperiodeVenter = VedtaksperiodeVenter(
            vedtaksperiodeId = node.vedtaksperiodeId,
            behandlingId = node.behandlingId,
            eksterneSøknadIder = eksterneSøknadIder,
            tidspunkt = opprettet,
            venteårsak = Venteårsak.valueOf(node.venterPå.venteårsak.hva),
            venterPåAnnenPeriode = node.vedtaksperiodeId != node.venterPå.vedtaksperiodeId,
            venterPåAnnenArbeidsgiver = node.organisasjonsnummer != node.venterPå.organisasjonsnummer
        )
        vedtaksperiodeVenter.håndter(sisPublisher)
    }

    private enum class Venteårsak { SØKNAD, INNTEKTSMELDING, GODKJENNING }

    private class VedtaksperiodeVenter private constructor(
        private val vedtaksperiodeId: UUID,
        private val behandlingId: UUID,
        private val eksterneSøknadIder: Set<UUID>,
        private val tidspunkt: OffsetDateTime,
        private val venterPå: VenterPå) {

        constructor(vedtaksperiodeId: UUID, behandlingId: UUID, eksterneSøknadIder: Set<UUID>, tidspunkt: OffsetDateTime, venteårsak: Venteårsak, venterPåAnnenPeriode: Boolean, venterPåAnnenArbeidsgiver: Boolean) :
                this(vedtaksperiodeId, behandlingId, eksterneSøknadIder, tidspunkt, venterPå(venteårsak, venterPåAnnenPeriode, venterPåAnnenArbeidsgiver))

        fun håndter(sisPublisher: SisPublisher) = venterPå.håndter(this, sisPublisher)

        private fun publiser(sisPublisher: SisPublisher, status: Behandlingstatustype) {
            sikkerlogg.info("Publiserer status $status som følge av at vedtaksperiode $vedtaksperiodeId, behandling $behandlingId venter på ${venterPå::class.simpleName}")
            sisPublisher.send(vedtaksperiodeId, Behandlingstatusmelding.behandlingstatus(vedtaksperiodeId, behandlingId, tidspunkt, status, eksterneSøknadIder))
        }

        private sealed interface VenterPå {
            fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher)
        }
        data object Godkjenning: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) =
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_SAKSBEHANDLER)
        }
        data object GodkjenningAnnenPeriode: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) =
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_SAKSBEHANDLER)
        }
        data object GodkjenningAnnenArbeidsgiver: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) =
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_SAKSBEHANDLER)
        }
        data object Inntektsmelding: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) {
                // Ettersom vi går automatisk til VENTER_PÅ_ARBEIDSGIVER ved opprettelse av behandling publiserer vi ikke noe
            }
        }
        data object InntektsmeldingAnnenPeriode: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) =
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_ANNEN_PERIODE)
        }
        data object InntektsmeldingAnnenArbeidsgiver: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) =
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_ANNEN_PERIODE)
        }
        data object Søknad: VenterPå {
            override fun håndter(vedtaksperiodeVenter: VedtaksperiodeVenter, sisPublisher: SisPublisher) =
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_ANNEN_PERIODE)
        }

        private companion object {
            private fun venterPå(venteårsak: Venteårsak, venterPåAnnenPeriode: Boolean, venterPåAnnenArbeidsgiver: Boolean): VenterPå {
                if (venteårsak == GODKJENNING && venterPåAnnenArbeidsgiver) return GodkjenningAnnenArbeidsgiver
                if (venteårsak == GODKJENNING && venterPåAnnenPeriode) return GodkjenningAnnenPeriode
                if (venteårsak == GODKJENNING) return Godkjenning

                if (venteårsak == INNTEKTSMELDING && venterPåAnnenArbeidsgiver) return InntektsmeldingAnnenArbeidsgiver
                if (venteårsak == INNTEKTSMELDING && venterPåAnnenPeriode) return InntektsmeldingAnnenPeriode
                if (venteårsak == INNTEKTSMELDING) return Inntektsmelding

                if (venteårsak == SØKNAD) return Søknad

                error("Klarte ikke evaluere hva vedtaksperioden venter på. venteårsak=$venteårsak, venterPåAnnenPeriode=$venterPåAnnenPeriode, venterPåAnnenArbeidsgiver=$venterPåAnnenArbeidsgiver")
            }
        }
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(VedtaksperiodeVenterRiver::class.java)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class VedtaksperiodeVenterDto(
    val organisasjonsnummer: String,
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val hendelser: Set<UUID>,
    val venterPå: VenterPå
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VenterPå(
        val vedtaksperiodeId: UUID,
        val organisasjonsnummer: String,
        val venteårsak: Venteårsak
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Venteårsak(
        val hva : String
    )
}