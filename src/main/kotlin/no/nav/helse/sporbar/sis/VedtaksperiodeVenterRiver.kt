package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.helse.sporbar.DokumentDao
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_ANNEN_PERIODE
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.asOffsetDateTime
import no.nav.helse.sporbar.sis.Behandlingstatusmelding.Companion.eksterneSøknadIder
import no.nav.helse.sporbar.sis.VedtaksperiodeVenterRiver.Venteårsak.GODKJENNING
import no.nav.helse.sporbar.sis.VedtaksperiodeVenterRiver.Venteårsak.INNTEKTSMELDING
import no.nav.helse.sporbar.sis.VedtaksperiodeVenterRiver.Venteårsak.SØKNAD
import org.slf4j.LoggerFactory

internal class VedtaksperiodeVenterRiver(rapid: RapidsConnection, private val dokumentDao: DokumentDao, private val sisPublisher: SisPublisher, ) : River.PacketListener {

    init {
        River(rapid).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_venter")
                it.demandAny("venterPå.venteårsak.hva", listOf("SØKNAD", "INNTEKTSMELDING", "GODKJENNING"))
                it.requireKey("vedtaksperiodeId", "behandlingId", "organisasjonsnummer", "venterPå.vedtaksperiodeId", "venterPå.organisasjonsnummer", "hendelser")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke vedtaksperiode_venter pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke vedtaksperiode_venter pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
        val interneHendelseIder = packet["hendelser"].map { it.asText().toUUID() }
        val eksterneSøknadIder = dokumentDao.eksterneSøknadIder(interneHendelseIder) ?: return sikkerlogg.error("Nå kom det en vedtaksperiode_venter uten at vi fant eksterne søknadIder. Er ikke dét rart?")
        val vedtaksperiodeVenter = VedtaksperiodeVenter(
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = packet["behandlingId"].asText().toUUID(),
            eksterneSøknadIder = eksterneSøknadIder,
            tidspunkt = packet["@opprettet"].asOffsetDateTime(),
            venteårsak = Venteårsak.valueOf(packet["venterPå.venteårsak.hva"].asText()),
            venterPåAnnenPeriode = vedtaksperiodeId != packet["venterPå.vedtaksperiodeId"].asText().toUUID(),
            venterPåAnnenArbeidsgiver = packet["organisasjonsnummer"].asText() != packet["venterPå.organisasjonsnummer"].asText()
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
