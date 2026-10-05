package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.*
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetMediator: VedtakFattetMediator,
    private val speedClient: SpeedClient,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "vedtak_fattet")
                }
                validate {
                    it.requireKey(
                        "fødselsnummer",
                        "@id",
                        "vedtaksperiodeId",
                        "organisasjonsnummer",
                        "yrkesaktivitetstype",
                        "hendelser",
                        "sykepengegrunnlag",
                        "tags",
                        "sykepengegrunnlagsfakta",
                    )
                    it.require("fom", JsonNode::asLocalDate)
                    it.require("tom", JsonNode::asLocalDate)
                    it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                    it.require("vedtakFattetTidspunkt", JsonNode::asLocalDateTime)
                    it.require("@opprettet", JsonNode::asLocalDateTime)
                    it.require("utbetalingId") { id -> UUID.fromString(id.asString()) }
                    it.interestedIn("begrunnelser")
                    it.interestedIn("saksbehandler", "saksbehandler.navn", "saksbehandler.ident")
                    it.interestedIn("beslutter", "beslutter.navn", "beslutter.ident")
                    it.interestedIn("forsikringsvurderingId")
                    it.interestedIn("utbetalingsdager")
                    it.interestedIn("vedtaksperiodeId") { id -> UUID.fromString(id.asString()) }
                    it.interestedIn("automatiskFattet", JsonNode::asBoolean)
                }
            }.register(this)
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("forstod ikke vedtak_fattet. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val callId = packet["@id"].asString()
        withMDC("callId" to callId) {
            håndterVedtakFattet(packet, callId)
        }
    }

    private fun håndterVedtakFattet(
        packet: JsonMessage,
        callId: String,
    ) {
        val ident = packet["fødselsnummer"].asString()
        val identer = retryBlocking { speedClient.hentFødselsnummerOgAktørId(ident, callId).getOrThrow() }

        val organisasjonsnummer = packet["organisasjonsnummer"].asString()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val hendelseIder = packet["hendelser"].values().map { UUID.fromString(it.asString()) }
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble()
        val vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asLocalDateTime()
        val begrunnelser =
            packet["begrunnelser"].takeUnless(JsonNode::isMissingOrNull)?.values()?.map { begrunnelse ->
                Begrunnelse(
                    begrunnelse["type"].asString(),
                    begrunnelse["begrunnelse"].asString(),
                    begrunnelse["perioder"].values().map {
                        Periode(it["fom"].asLocalDate(), it["tom"].asLocalDate())
                    },
                )
            } ?: emptyList()
        val automatiskFattet = packet["automatiskFattet"].asBoolean()
        val tags =
            packet["tags"]
                .takeUnless(JsonNode::isMissingOrNull)
                ?.values()
                ?.map { it.asString() }
                ?.filter { tag -> tag in TAGS_TIL_DELING_UTAD }
                ?.toSet() ?: emptySet<String>()
        val utbetalingId = UUID.fromString(packet["utbetalingId"].asString())
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asString())
        val utbetalingsdager =
            packet["utbetalingsdager"].values().map {
                Utbetalingsdag(
                    dato = it["dato"].asLocalDate(),
                    type = it["type"].asString(),
                    sykdomsgrad = it["sykdomsgrad"].asInt(),
                    dekningsgrad = it["dekningsgrad"].asInt(),
                    beløpTilBruker = it["beløpTilBruker"].asInt(),
                    beløpTilArbeidsgiver = it["beløpTilArbeidsgiver"].asInt(),
                    begrunnelser = it["begrunnelser"].values().map { begrunnelse -> begrunnelse.asString() },
                )
            }
        val yrkesaktivitetstype = packet["yrkesaktivitetstype"].asString()
        val sykepengegrunnlagsfakta = packet["sykepengegrunnlagsfakta"].asSykepengegrunnlagsfakta(yrkesaktivitetstype)
        val saksbehandlerNavnOgIdent =
            packet["saksbehandler"].takeUnless { it.isMissingOrNull() }?.let {
                NavnOgIdent(
                    it["navn"].asString(),
                    it["ident"].asString(),
                )
            }
        val beslutterNavnOgIdent =
            packet["beslutter"].takeUnless { it.isMissingOrNull() }?.let {
                NavnOgIdent(
                    it["navn"].asString(),
                    it["ident"].asString(),
                )
            }

        vedtakFattetMediator.vedtakFattet(
            VedtakFattet(
                fødselsnummer = identer.fødselsnummer,
                aktørId = identer.aktørId,
                organisasjonsnummer = organisasjonsnummer,
                yrkesaktivitetstype = yrkesaktivitetstype,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = skjæringstidspunkt,
                hendelseIder = hendelseIder,
                sykepengegrunnlag = sykepengegrunnlag,
                utbetalingId = utbetalingId,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                sykepengegrunnlagsfakta = sykepengegrunnlagsfakta,
                begrunnelser = begrunnelser,
                tags = tags,
                saksbehandlerNavnOgIdent = saksbehandlerNavnOgIdent,
                beslutterNavnOgIdent = beslutterNavnOgIdent,
                forsikringsvurderingId =
                    packet["forsikringsvurderingId"]
                        .takeUnless { it.isMissingOrNull() }
                        ?.let { UUID.fromString(it.asString()) },
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingsdager = utbetalingsdager,
                automatiskFattet = automatiskFattet,
            ),
        )
        log.info("Behandler vedtakFattet: ${packet["@id"].asString()}")
        sikkerLog.info("Behandler vedtakFattet: ${packet["@id"].asString()}")
    }

    private fun JsonNode.asSykepengegrunnlagsfakta(yrkesaktivitetstype: String) =
        if (yrkesaktivitetstype == "SELVSTENDIG") {
            when (val fastsatt = this["fastsatt"].asString()) {
                "EtterHovedregel" -> {
                    SykepengegrunnlagsfaktaSelvstendigNæringsdrivende(
                        `6G` = this["6G"].asBigDecimal(),
                        tags = get("tags").values().map { it.asString() }.toSet(),
                        selvstendig =
                            SykepengegrunnlagsfaktaSelvstendigNæringsdrivende.Selvstendig(
                                beregningsgrunnlag = this["selvstendig"]["beregningsgrunnlag"].asBigDecimal(),
                                pensjonsgivendeInntekter =
                                    this["selvstendig"]["pensjonsgivendeInntekter"].values().map {
                                        SykepengegrunnlagsfaktaSelvstendigNæringsdrivende.Selvstendig.PensjonsgivendeInntekt(
                                            årstall = it["årstall"].asInt(),
                                            beløp = it["beløp"].asBigDecimal(),
                                        )
                                    },
                            ),
                    )
                }

                else -> {
                    "Støtter ikke sykepengegrunnlag fastsatt \"$fastsatt\" for yrkesaktivitetstype \"$yrkesaktivitetstype\"".let { feilmelding ->
                        sikkerLog.error("${feilmelding}\n\n\t$this")
                        error(feilmelding)
                    }
                }
            }
        } else {
            when (val fastsatt = this["fastsatt"].asString()) {
                "EtterHovedregel" ->
                    FastsattEtterHovedregel(
                        omregnetÅrsinntekt = get("omregnetÅrsinntekt").asDouble(),
                        innrapportertÅrsinntekt = get("innrapportertÅrsinntekt").asDouble(),
                        avviksprosent = get("avviksprosent").asDouble(),
                        `6G` = get("6G").asDouble(),
                        tags = get("tags").values().map { it.asString() }.toSet(),
                        arbeidsgivere =
                            get("arbeidsgivere").values().map {
                                FastsattEtterHovedregel.Arbeidsgiver(
                                    arbeidsgiver = it.get("arbeidsgiver").asString(),
                                    omregnetÅrsinntekt = it.get("omregnetÅrsinntekt").asDouble(),
                                )
                            },
                    )

                "EtterSkjønn" ->
                    FastsattEtterSkjønn(
                        omregnetÅrsinntekt = get("omregnetÅrsinntekt").asDouble(),
                        innrapportertÅrsinntekt = get("innrapportertÅrsinntekt").asDouble(),
                        skjønnsfastsatt = get("skjønnsfastsatt").asDouble(),
                        avviksprosent = get("avviksprosent").asDouble(),
                        `6G` = get("6G").asDouble(),
                        tags = get("tags").values().map { it.asString() }.toSet(),
                        arbeidsgivere =
                            get("arbeidsgivere").values().map {
                                FastsattEtterSkjønn.Arbeidsgiver(
                                    arbeidsgiver = it.get("arbeidsgiver").asString(),
                                    omregnetÅrsinntekt = it.get("omregnetÅrsinntekt").asDouble(),
                                    skjønnsfastsatt = it.get("skjønnsfastsatt").asDouble(),
                                )
                            },
                    )

                "IInfotrygd" -> FastsattIInfotrygd(get("omregnetÅrsinntekt").asDouble())

                else -> {
                    "Støtter ikke sykepengegrunnlag fastsatt \"$fastsatt\" for yrkesaktivitetstype \"$yrkesaktivitetstype\"".let { feilmelding ->
                        sikkerLog.error("${feilmelding}\n\n\t$this")
                        throw IllegalStateException(feilmelding)
                    }
                }
            }
        }

    private fun JsonNode.asBigDecimal(): BigDecimal = BigDecimal(asString())

    companion object {
        val TAGS_TIL_DELING_UTAD: Set<String> =
            setOf(
                "IngenNyArbeidsgiverperiode",
                "SykepengegrunnlagUnder2G",
                "InntektFraAOrdningenLagtTilGrunn",
                "ArbeidsgiverØnskerRefusjon",
            )
    }
}

internal class Begrunnelse(
    val type: String,
    val begrunnelse: String,
    val perioder: List<Periode>,
)

internal class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)

internal data class VedtakFattet(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val hendelseIder: List<UUID>,
    val sykepengegrunnlag: Double,
    val utbetalingId: UUID,
    val vedtaksperiodeId: UUID,
    val vedtakFattetTidspunkt: LocalDateTime,
    val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta,
    val begrunnelser: List<Begrunnelse>,
    val tags: Set<String>,
    val saksbehandlerNavnOgIdent: NavnOgIdent?,
    val beslutterNavnOgIdent: NavnOgIdent?,
    val forsikringsvurderingId: UUID?,
    val utbetalingsdager: List<Utbetalingsdag>,
    val automatiskFattet: Boolean,
)

sealed class Sykepengegrunnlagsfakta(
    internal val fastsatt: String,
)

internal class FastsattEtterHovedregel(
    val omregnetÅrsinntekt: Double,
    internal val innrapportertÅrsinntekt: Double,
    internal val avviksprosent: Double,
    internal val `6G`: Double,
    internal val tags: Set<String>,
    internal val arbeidsgivere: List<Arbeidsgiver>,
) : Sykepengegrunnlagsfakta("EtterHovedregel") {
    internal class Arbeidsgiver(
        internal val arbeidsgiver: String,
        internal val omregnetÅrsinntekt: Double,
    )
}

internal class FastsattEtterSkjønn(
    val omregnetÅrsinntekt: Double,
    internal val innrapportertÅrsinntekt: Double,
    internal val skjønnsfastsatt: Double,
    internal val avviksprosent: Double,
    internal val `6G`: Double,
    internal val tags: Set<String>,
    internal val arbeidsgivere: List<Arbeidsgiver>,
) : Sykepengegrunnlagsfakta("EtterSkjønn") {
    internal class Arbeidsgiver(
        internal val arbeidsgiver: String,
        internal val omregnetÅrsinntekt: Double,
        internal val skjønnsfastsatt: Double,
    )
}

internal class FastsattIInfotrygd(
    val omregnetÅrsinntekt: Double,
) : Sykepengegrunnlagsfakta("IInfotrygd")

data class SykepengegrunnlagsfaktaSelvstendigNæringsdrivende(
    val `6G`: BigDecimal,
    val tags: Set<String>,
    val selvstendig: Selvstendig,
) : Sykepengegrunnlagsfakta("EtterHovedregel") {
    data class Selvstendig(
        val beregningsgrunnlag: BigDecimal,
        val pensjonsgivendeInntekter: List<PensjonsgivendeInntekt>,
    ) {
        data class PensjonsgivendeInntekt(
            val årstall: Int,
            val beløp: BigDecimal,
        )
    }
}
