package no.nav.helse.sporbar.inntektsmelding

import java.time.LocalDateTime
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sporbar.inntektsmelding.Status.TRENGER_IKKE_INNTEKTSMELDING
import no.nav.helse.sporbar.inntektsmelding.Status.TRENGER_INNTEKTSMELDING

class InntektsmeldingDao(private val dataSource: DataSource) {

    private fun InntektsmeldingPakke.tabell() = when (status) {
        TRENGER_INNTEKTSMELDING -> "trenger_inntektsmelding"
        TRENGER_IKKE_INNTEKTSMELDING -> "trenger_ikke_inntektsmelding"
        else -> throw IllegalStateException("Ugyldig status $status")
    }

    internal fun lagre(melding: InntektsmeldingPakke) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO ${melding.tabell()}(id, hendelse_id, hendelse_opprettet, fodselsnummer, orgnummer, vedtaksperiode_id, fom, tom, melding_innsatt, data)  " +
                            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING",
                    melding.id,
                    melding.hendelseId,
                    melding.opprettet,
                    melding.fødselsnummer,
                    melding.organisasjonsnummer,
                    melding.vedtaksperiodeId,
                    melding.fom,
                    melding.tom,
                    LocalDateTime.now(),
                    melding.json.toJson()
                ).asUpdate
            )
        }
    }
}