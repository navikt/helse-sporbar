package no.nav.helse.sporbar.inntektsmelding

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime

internal enum class InntektsmeldingStatus(internal val eksternDto: String) {
    TRENGER_INNTEKTSMELDING("MANGLER_INNTEKTSMELDING"),
    TRENGER_IKKE_INNTEKTSMELDING("MANGLER_IKKE_INNTEKTSMELDING"),
    FORKASTET("BEHANDLES_UTENFOR_SPLEIS")
}

internal class InntektsmeldingPakke(
    val id: UUID,
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val vedtaksperiodeId: UUID,
    val status: InntektsmeldingStatus,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime,
    val json: JsonMessage
)

internal fun JsonMessage.somInntektsmeldingPakke(status: InntektsmeldingStatus): InntektsmeldingPakke {
    val id = UUID.randomUUID()
    val hendelseId = UUID.fromString(this["@id"].asText())
    val fødselsnummer = this["fødselsnummer"].asText()
    val aktørId = this["aktørId"].asText()
    val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
    val organisasjonsnummer = this["organisasjonsnummer"].asText()
    val fom = this["fom"].asLocalDate()
    val tom = this["tom"].asLocalDate()
    val opprettet = this["@opprettet"].asLocalDateTime()

    return InntektsmeldingPakke(
        id = id,
        hendelseId = hendelseId,
        fødselsnummer = fødselsnummer,
        aktørId = aktørId,
        organisasjonsnummer = organisasjonsnummer,
        vedtaksperiodeId = vedtaksperiodeId,
        status = status,
        fom = fom,
        tom = tom,
        opprettet = opprettet,
        json = this
    )
}

internal fun Pair<UUID, InntektsmeldingPakke>.tilEksternDto() = let { (id, pakke) ->
"""
    {
        "id": "$id",
        "status": "${pakke.status.eksternDto}",
        "sykmeldt": "${pakke.fødselsnummer}",
        "arbeidsgiver": "${pakke.organisasjonsnummer}",
        "vedtaksperiode": {
            "id": "${pakke.vedtaksperiodeId}",
            "fom": "${pakke.fom}",
            "tom": "${pakke.tom}"
        },
        "tidsstempel": "${pakke.opprettet}",
        "versjon": "1.0.0"
    }
"""
}

