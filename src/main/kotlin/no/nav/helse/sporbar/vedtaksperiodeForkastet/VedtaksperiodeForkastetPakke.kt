package no.nav.helse.sporbar.vedtaksperiodeForkastet

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime

internal class VedtaksperiodeForkastetPakke(
    val id: UUID,
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime,
    val json: JsonMessage
)

internal fun JsonMessage.somVedtaksperiodeForkastetPakke(): VedtaksperiodeForkastetPakke {
    val id = UUID.randomUUID()
    val hendelseId = UUID.fromString(this["@id"].asText())
    val fødselsnummer = this["fødselsnummer"].asText()
    val aktørId = this["aktørId"].asText()
    val vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText())
    val organisasjonsnummer = this["organisasjonsnummer"].asText()
    val fom = this["fom"].asLocalDate()
    val tom = this["tom"].asLocalDate()
    val opprettet = this["@opprettet"].asLocalDateTime()

    return VedtaksperiodeForkastetPakke(
        id = id,
        hendelseId = hendelseId,
        fødselsnummer = fødselsnummer,
        aktørId = aktørId,
        organisasjonsnummer = organisasjonsnummer,
        vedtaksperiodeId = vedtaksperiodeId,
        fom = fom,
        tom = tom,
        opprettet = opprettet,
        json = this
    )
}