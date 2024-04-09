package no.nav.helse.sporbar

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.sporbar.Sykepengegrunnlagsfakta.Companion.sykepengegrunnlagsfakta
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetMediator: VedtakFattetMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtak_fattet")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "hendelser",
                    "sykepengegrunnlag",
                    "grunnlagForSykepengegrunnlag",
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver",
                    "begrensning",
                    "inntekt"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                it.require("vedtakFattetTidspunkt", JsonNode::asLocalDateTime)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.interestedIn("utbetalingId") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("sykepengegrunnlagsfakta")
                it.interestedIn("begrunnelser", "tags")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke vedtak_fattet. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val hendelseIder = packet["hendelser"].map { UUID.fromString(it.asText()) }
        val inntekt = packet["inntekt"].asDouble()
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble()
        val grunnlagForSykepengegrunnlag = packet["grunnlagForSykepengegrunnlag"].asDouble()
        val grunnlagForSykepengegrunnlagPerArbeidsgiver = objectMapper.readValue(packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString(), object : TypeReference<Map<String, Double>>() {})
        val begrensning = packet["begrensning"].asText()
        val vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asLocalDateTime()
        val begrunnelser = packet["begrunnelser"].takeUnless(JsonNode::isMissingOrNull)?.map { begrunnelse ->
            Begrunnelse(
                begrunnelse["type"].asText(),
                begrunnelse["begrunnelse"].asText(),
                begrunnelse["perioder"].map {
                    Periode(it["fom"].asLocalDate(), it["tom"].asLocalDate())
                }
            )
        } ?: emptyList()

        val tags = packet["tags"].takeUnless(JsonNode::isMissingOrNull)?.map { it.asText() }
            ?.filter { tag -> tag in TAGS_TIL_DELING_UTAD }?.toSet() ?: emptySet<String>()

        val utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let {
            UUID.fromString(it.asText())
        }
        val sykepengegrunnlagsfakta = packet["sykepengegrunnlagsfakta"].takeUnless(JsonNode::isMissingOrNull)?.sykepengegrunnlagsfakta

        vedtakFattetMediator.vedtakFattet(
            VedtakFattet(
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer,
                fom = fom,
                tom = tom,
                skjæringstidspunkt = skjæringstidspunkt,
                hendelseIder = hendelseIder,
                inntekt = inntekt,
                sykepengegrunnlag = sykepengegrunnlag,
                grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
                grunnlagForSykepengegrunnlagPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver,
                begrensning = begrensning,
                utbetalingId = utbetalingId,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                sykepengegrunnlagsfakta = sykepengegrunnlagsfakta,
                begrunnelser = begrunnelser,
                tags = tags
            )
        )
        log.info("Behandler vedtakFattet: ${packet["@id"].asText()}")
        sikkerLog.info("Behandler vedtakFattet: ${packet["@id"].asText()}")
    }

    companion object {
        val TAGS_TIL_DELING_UTAD: Set<String> = setOf("IngenNyArbeidsgiverperiode", "SykepengegrunnlagUnder2G")
    }
}

internal class Begrunnelse(
    val type: String,
    val begrunnelse: String,
    val perioder: List<Periode>
)

internal class Periode(
    val fom: LocalDate,
    val tom: LocalDate
)

internal data class VedtakFattet(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val hendelseIder: List<UUID>,
    val inntekt: Double,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>,
    val begrensning: String,
    val utbetalingId: UUID?,
    val vedtakFattetTidspunkt: LocalDateTime,
    val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta?,
    val begrunnelser: List<Begrunnelse>,
    val tags: Set<String>
)

internal sealed class Sykepengegrunnlagsfakta(internal val fastsatt: String, internal val omregnetÅrsinntekt: Double) {
    internal companion object {
        internal val JsonNode.sykepengegrunnlagsfakta get() = when (path("fastsatt").asText()) {
            "EtterHovedregel" -> FastsattEtterHovedregel(
                omregnetÅrsinntekt = get("omregnetÅrsinntekt").asDouble(),
                innrapportertÅrsinntekt = get("innrapportertÅrsinntekt").asDouble(),
                avviksprosent = get("avviksprosent").asDouble(),
                `6G` = get("6G").asDouble(),
                tags = get("tags").map { it.asText() }.toSet(),
                arbeidsgivere = get("arbeidsgivere").map { FastsattEtterHovedregel.Arbeidsgiver(
                    arbeidsgiver = it.get("arbeidsgiver").asText(),
                    omregnetÅrsinntekt = it.get("omregnetÅrsinntekt").asDouble()
                )}
            )
            "EtterSkjønn" -> FastsattEtterSkjønn(
                omregnetÅrsinntekt = get("omregnetÅrsinntekt").asDouble(),
                innrapportertÅrsinntekt = get("innrapportertÅrsinntekt").asDouble(),
                skjønnsfastsatt = get("skjønnsfastsatt").asDouble(),
                avviksprosent = get("avviksprosent").asDouble(),
                `6G` = get("6G").asDouble(),
                tags = get("tags").map { it.asText() }.toSet(),
                arbeidsgivere = get("arbeidsgivere").map { FastsattEtterSkjønn.Arbeidsgiver(
                    arbeidsgiver = it.get("arbeidsgiver").asText(),
                    omregnetÅrsinntekt = it.get("omregnetÅrsinntekt").asDouble(),
                    skjønnsfastsatt = it.get("skjønnsfastsatt").asDouble()
                )}
            )
            "IInfotrygd" -> FastsattIInfotrygd(get("omregnetÅrsinntekt").asDouble())
            else -> {
                "Støtter ikke sykepengegrunnlag fastsatt ${path("fastsatt").asText()}".let { feilmelding ->
                    sikkerLog.error("${feilmelding}\n\n\t${this}")
                    throw IllegalStateException(feilmelding)
                }
            }
        }
    }
}

internal class FastsattEtterHovedregel(
    omregnetÅrsinntekt: Double,
    internal val innrapportertÅrsinntekt: Double,
    internal val avviksprosent: Double,
    internal val `6G`: Double,
    internal val tags: Set<String>,
    internal val arbeidsgivere: List<Arbeidsgiver>
) : Sykepengegrunnlagsfakta("EtterHovedregel", omregnetÅrsinntekt) {
    internal class Arbeidsgiver(internal val arbeidsgiver: String, internal val omregnetÅrsinntekt: Double)
}

internal class FastsattEtterSkjønn(
    omregnetÅrsinntekt: Double,
    internal val innrapportertÅrsinntekt: Double,
    internal val skjønnsfastsatt: Double,
    internal val avviksprosent: Double,
    internal val `6G`: Double,
    internal val tags: Set<String>,
    internal val arbeidsgivere: List<Arbeidsgiver>
) : Sykepengegrunnlagsfakta("EtterSkjønn", omregnetÅrsinntekt) {
    internal class Arbeidsgiver(internal val arbeidsgiver: String, internal val omregnetÅrsinntekt: Double, internal val skjønnsfastsatt: Double)
}

internal class FastsattIInfotrygd(
    omregnetÅrsinntekt: Double,
) : Sykepengegrunnlagsfakta("IInfotrygd", omregnetÅrsinntekt)