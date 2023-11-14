package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sporbar.UtbetalingUtbetalt.OppdragDto.Companion.parseOppdrag
import no.nav.helse.sporbar.UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.Companion.parseLinje
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

private val NULLE_UT_TOMME_OPPDRAG = System.getenv("NULLE_UT_TOMME_OPPDRAG")?.toBoolean() ?: false

internal class UtbetalingUtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingMediator: UtbetalingMediator,
    private val spesialsakDao: SpesialsakDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_utbetalt")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "stønadsdager",
                    "automatiskBehandling",
                    "arbeidsgiverOppdrag",
                    "personOppdrag",
                    "utbetalingsdager",
                    "type",
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
                it.require("korrelasjonsId") { id -> UUID.fromString(id.asText()) }
                it.interestedIn("vedtaksperiodeIder")

                it.requireKey("arbeidsgiverOppdrag.mottaker", "arbeidsgiverOppdrag.fagområde", "arbeidsgiverOppdrag.fagsystemId",
                    "arbeidsgiverOppdrag.nettoBeløp", "arbeidsgiverOppdrag.stønadsdager")
                it.require("arbeidsgiverOppdrag.fom", JsonNode::asLocalDate)
                it.require("arbeidsgiverOppdrag.tom", JsonNode::asLocalDate)
                it.requireArray("arbeidsgiverOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("sats", "totalbeløp", "grad", "stønadsdager")
                }
                it.requireKey("personOppdrag.mottaker", "personOppdrag.fagområde", "personOppdrag.fagsystemId",
                    "personOppdrag.nettoBeløp", "personOppdrag.stønadsdager")
                it.require("personOppdrag.fom", JsonNode::asLocalDate)
                it.require("personOppdrag.tom", JsonNode::asLocalDate)
                it.requireArray("personOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("sats", "totalbeløp", "grad", "stønadsdager")
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke utbetaling_utbetalt. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke utbetaling_utbetalt:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val maksdato = packet["maksdato"].asLocalDate()
        val utbetalingId = packet["utbetalingId"].let{ UUID.fromString(it.asText())}
        val korrelasjonsId = packet["korrelasjonsId"].let{ UUID.fromString(it.asText())}
        val forbrukteSykedager = packet["forbrukteSykedager"].asInt()
        val gjenståendeSykedager = packet["gjenståendeSykedager"].asInt()
        val stønadsdager = packet["stønadsdager"].asInt()
        val automatiskBehandling = packet["automatiskBehandling"].asBoolean()
        val type = packet["type"].asText()
        val utbetalingsdager = packet["utbetalingsdager"].toList().map { dag ->
            UtbetalingUtbetalt.UtbetalingdagDto(
                dato = dag["dato"].asLocalDate(),
                type = dag["type"].dagtype,
                begrunnelser = dag.path("begrunnelser").takeUnless(JsonNode::isMissingOrNull)
                    ?.let { mapBegrunnelser(it.toList())} ?: emptyList()
            )
        }

        val arbeidsgiverOppdrag = parseOppdrag(packet["arbeidsgiverOppdrag"])
        val personOppdrag = parseOppdrag(packet["personOppdrag"])

        val vedtaksperiode = packet["vedtaksperiodeIder"].map(JsonNode::asText).singleOrNull()
        if (vedtaksperiode != null && IngenMeldingOmVedtak(spesialsakDao).ignorerMeldingOmVedtak(vedtaksperiode, arbeidsgiverOppdrag, personOppdrag)) {
            return sikkerLog.info("Ignorerer melding om vedtak for $vedtaksperiode")
        }

        utbetalingMediator.utbetalingUtbetalt(
            UtbetalingUtbetalt(
                event = "utbetaling_utbetalt",
                utbetalingId = utbetalingId,
                korrelasjonsId = korrelasjonsId,
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer,
                fom = fom,
                tom = tom,
                forbrukteSykedager = forbrukteSykedager,
                gjenståendeSykedager = gjenståendeSykedager,
                stønadsdager = stønadsdager,
                automatiskBehandling = automatiskBehandling,
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                personOppdrag = personOppdrag,
                type = type,
                utbetalingsdager = utbetalingsdager,
                antallVedtak = 1,
                foreløpigBeregnetSluttPåSykepenger = maksdato
            )
        )
        log.info("Behandler utbetaling_utbetalt: ${packet["@id"].asText()}")
        sikkerLog.info("Behandler utbetaling_utbetalt: ${packet["@id"].asText()}")
    }
}

data class UtbetalingUtbetalt(
    val event: String,
    val utbetalingId: UUID,
    val korrelasjonsId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val stønadsdager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto?,
    val personOppdrag: OppdragDto?,
    val type: String,
    val utbetalingsdager: List<UtbetalingdagDto>,
    val antallVedtak: Int?,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate
) {
    enum class Begrunnelse {
        AndreYtelserAap,
        AndreYtelserDagpenger,
        AndreYtelserForeldrepenger,
        AndreYtelserOmsorgspenger,
        AndreYtelserOpplaringspenger,
        AndreYtelserPleiepenger,
        AndreYtelserSvangerskapspenger,
        SykepengedagerOppbrukt,
        SykepengedagerOppbruktOver67,
        MinimumInntekt,
        EgenmeldingUtenforArbeidsgiverperiode,
        MinimumSykdomsgrad,
        ManglerOpptjening,
        ManglerMedlemskap,
        EtterDødsdato,
        Over70,
        MinimumInntektOver67,
        NyVilkårsprøvingNødvendig,
        UKJENT
    }

        data class OppdragDto(
            val mottaker: String,
            val fagområde: String,
            val fagsystemId: String,
            val nettoBeløp: Int,
            val stønadsdager: Int,
            val fom: LocalDate,
            val tom: LocalDate,
            val utbetalingslinjer: List<UtbetalingslinjeDto>
        ) {
            companion object {
                fun parseOppdrag(oppdrag: JsonNode) =
                    OppdragDto(
                        mottaker = oppdrag["mottaker"].asText(),
                        fagområde = oppdrag["fagområde"].asText(),
                        fagsystemId = oppdrag["fagsystemId"].asText(),
                        nettoBeløp = oppdrag["nettoBeløp"].asInt(),
                        stønadsdager = oppdrag["stønadsdager"].asInt(),
                        fom = oppdrag["fom"].asLocalDate(),
                        tom = oppdrag["tom"].asLocalDate(),
                        utbetalingslinjer = oppdrag["linjer"].map { linje -> parseLinje(linje) }
                    ).takeUnless { NULLE_UT_TOMME_OPPDRAG && it.utbetalingslinjer.isEmpty() }
            }

            data class UtbetalingslinjeDto(
                val fom: LocalDate,
                val tom: LocalDate,
                val dagsats: Int,
                val totalbeløp: Int,
                val grad: Double,
                val stønadsdager: Int
            ) {
                companion object {
                    fun parseLinje(linje: JsonNode) =
                        UtbetalingslinjeDto(
                            fom = linje["fom"].asLocalDate(),
                            tom = linje["tom"].asLocalDate(),
                            dagsats = linje["sats"].asInt(),
                            totalbeløp = linje["totalbeløp"].asInt(),
                            grad = linje["grad"].asDouble(),
                            stønadsdager = linje["stønadsdager"].asInt()
                        )
                }
            }
        }
        data class UtbetalingdagDto(
            val dato: LocalDate,
            val type: String,
            val begrunnelser: List<Begrunnelse>
        )
}

internal fun mapBegrunnelser(begrunnelser: List<JsonNode>): List<UtbetalingUtbetalt.Begrunnelse> = begrunnelser.map {
    when (it.asText()) {
        "SykepengedagerOppbrukt" -> UtbetalingUtbetalt.Begrunnelse.SykepengedagerOppbrukt
        "SykepengedagerOppbruktOver67" -> UtbetalingUtbetalt.Begrunnelse.SykepengedagerOppbruktOver67
        "MinimumInntekt" -> UtbetalingUtbetalt.Begrunnelse.MinimumInntekt
        "MinimumInntektOver67" -> UtbetalingUtbetalt.Begrunnelse.MinimumInntektOver67
        "EgenmeldingUtenforArbeidsgiverperiode" -> UtbetalingUtbetalt.Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode
        "AndreYtelserAap" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserAap
        "AndreYtelserDagpenger" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserDagpenger
        "AndreYtelserForeldrepenger" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserForeldrepenger
        "AndreYtelserOmsorgspenger" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserOmsorgspenger
        "AndreYtelserOpplaringspenger" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserOpplaringspenger
        "AndreYtelserPleiepenger" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserPleiepenger
        "AndreYtelserSvangerskapspenger" -> UtbetalingUtbetalt.Begrunnelse.AndreYtelserSvangerskapspenger
        "MinimumSykdomsgrad" -> UtbetalingUtbetalt.Begrunnelse.MinimumSykdomsgrad
        "ManglerOpptjening" -> UtbetalingUtbetalt.Begrunnelse.ManglerOpptjening
        "ManglerMedlemskap" -> UtbetalingUtbetalt.Begrunnelse.ManglerMedlemskap
        "EtterDødsdato" -> UtbetalingUtbetalt.Begrunnelse.EtterDødsdato
        "Over70" -> UtbetalingUtbetalt.Begrunnelse.Over70
        "NyVilkårsprøvingNødvendig" -> UtbetalingUtbetalt.Begrunnelse.NyVilkårsprøvingNødvendig
        else -> {
            log.error("Ukjent begrunnelse $it")
            UtbetalingUtbetalt.Begrunnelse.UKJENT
        }
    }
}

private val KjenteDagtyper = setOf(
    "ArbeidsgiverperiodeDag",
    "NavDag",
    "NavHelgDag",
    "Arbeidsdag",
    "Fridag",
    "AvvistDag",
    "UkjentDag",
    "ForeldetDag",
    "Permisjonsdag",
    "Feriedag",
    "ArbeidIkkeGjenopptattDag"
)

internal val JsonNode.dagtype get(): String {
    val fraSpleis = asText()
    if (fraSpleis in KjenteDagtyper) return fraSpleis
    throw IllegalStateException("Ny dagtype fra Spleis: $fraSpleis. Vurder om denne skal eksponeres videre ut på tbd.utbetaling")
}