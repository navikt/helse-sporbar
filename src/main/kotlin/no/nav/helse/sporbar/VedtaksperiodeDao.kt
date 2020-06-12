package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class VedtaksperiodeDao(private val dataSource: DataSource) {
    internal fun finn(fødselsnummer: String): List<Vedtaksperiode> {
        @Language("PostgreSQL")
        val query = """SELECT *
                       FROM vedtaksperiode v
                       WHERE v.fodselsnummer = ?
                       """
        return sessionOf(dataSource)
            .use { session ->
                session.run(
                    queryOf(query, fødselsnummer)
                        .map { row ->
                            Vedtaksperiode(
                                vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                                fnr = row.string("fodselsnummer"),
                                orgnummer = row.string("orgnummer"),
                                vedtak = null, //TODO: koble på vedtak
                                dokumenter = emptyList() //TODO: koble på vedtak_dokument
                            )
                        }
                        .asList
                )
            }
    }

    internal fun opprett(
        fnr: String,
        orgnummer: String,
        vedtaksperiodeId: UUID,
        hendelseIder: List<UUID>
    ) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtaksperiode(vedtaksperiode_id, fodselsnummer, orgnummer) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;
                       INSERT INTO vedtak_dokument(vedtaksperiode_id, dokument_id)
                           (SELECT
                                   (SELECT id from vedtaksperiode WHERE vedtaksperiode_id = ?),
                                   d.id
                            FROM hendelse h
                                   INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                                   INNER JOIN dokument d on hd.dokument_id = d.id
                            WHERE h.hendelse_id = ANY ((?)::uuid[]))
        """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    vedtaksperiodeId,
                    fnr,
                    orgnummer,
                    vedtaksperiodeId,
                    hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() }
                ).asExecute
            )
        }
    }
}
