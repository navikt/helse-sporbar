package no.nav.helse.sporbar.inntektsmelding

import java.time.LocalDateTime
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf

class InntektsmeldingDao(private val dataSource: DataSource) {

    internal fun trengerInntektsmelding(melding: InntektsmeldingPakke) {
        insert(melding, tabellNavn = "trenger_inntektsmelding")
    }

    internal fun trengerIkkeInntektsmelding(melding: InntektsmeldingPakke) {
        insert(melding, tabellNavn = "trenger_ikke_inntektsmelding")
    }

    private fun insert(melding: InntektsmeldingPakke, tabellNavn: String) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO ${tabellNavn}(id, hendelse_id, hendelse_opprettet, fodselsnummer, orgnummer, vedtaksperiode_id, fom, tom, melding_innsatt, data)  " +
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