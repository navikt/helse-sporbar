package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
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
import java.time.OffsetDateTime
import java.util.*
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER

internal class VedtaksperiodeVenterRiver(rapid: RapidsConnection, private val spedisjonClient: SpedisjonClient, private val sisPublisher: SisPublisher) : River.PacketListener {

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
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.info("Håndterer ikke vedtaksperioder_venter pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke vedtaksperioder_venter pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val opprettet = packet["@opprettet"].asOffsetDateTime()
        packet["vedtaksperioder"].forEach { venter ->
            try {
                val callId = UUID.randomUUID().toString()
                val venterDto = objectMapper.convertValue<VedtaksperiodeVenterDto>(venter)
                withMDC(mapOf(
                    "vedtaksperiodeId" to "${venterDto.vedtaksperiodeId}",
                    "behandlingId" to "${venterDto.behandlingId}",
                    "callId" to callId,
                )) {
                    håndterVedtaksperiodeVenter(callId, opprettet, venterDto)
                }
            } catch (err: Exception) {
                sikkerlogg.error("Kunne ikke tolke vedtaksperiode venter: ${err.message}", err)
            }
        }
    }

    private fun håndterVedtaksperiodeVenter(callId: String, opprettet: OffsetDateTime, node: VedtaksperiodeVenterDto) {
        if (node.venterPå.venteårsak.hva !in listOf("SØKNAD", "INNTEKTSMELDING", "GODKJENNING")) return

        val hendelser = node.hendelser
        logg.info("Henter dokumenter $hendelser")
        sikkerlogg.info("Henter dokumenter $hendelser")
        val eksterneSøknadIder = retryBlocking {
            spedisjonClient.hentMeldinger(hendelser, callId).getOrThrow().tilSøknader()
        } ?: emptySet()
        if (eksterneSøknadIder.isEmpty()) sikkerlogg.info("Nå kom det en vedtaksperiode_venter uten at vi fant eksterne søknadIder. Det er ikke rart.")

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
                vedtaksperiodeVenter.publiser(sisPublisher, VENTER_PÅ_ARBEIDSGIVER)
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
    val hendelser: List<UUID>,
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
