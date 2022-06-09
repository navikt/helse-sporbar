package no.nav.helse.sporbar.vedtaksperiodeForkastet

import java.time.LocalDateTime
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf

internal class VedtaksperiodeForkastetDao(private val dataSource: DataSource) {

    internal fun lagre(melding: VedtaksperiodeForkastetPakke) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO vedtaksperiode_forkastet (id, hendelse_id, hendelse_opprettet, fodselsnummer, orgnummer, vedtaksperiode_id, fom, tom, melding_innsatt, data)  " +
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