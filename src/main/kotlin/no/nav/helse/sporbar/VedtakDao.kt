package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

internal class VedtakDao(private val dataSource: DataSource) {
    internal fun opprett(
        fom: LocalDate,
        tom: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        hendelseIder: List<UUID>
    ) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtak(fom, tom, forbrukte_sykedager, gjenstaende_sykedager, vedtaksperiode_id) VALUES (
                       ?,
                       ?,
                       ?,
                       ?,
                       (SELECT distinct vd.vedtaksperiode_id
                        FROM hendelse h
                               INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                               INNER JOIN dokument d on hd.dokument_id = d.id
                               INNER JOIN vedtak_dokument vd on d.id = vd.dokument_id
                        WHERE h.hendelse_id = ANY ((?)::uuid[])
                        AND d.type = ?))
                        ON CONFLICT DO NOTHING;
        """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    fom, tom, forbrukteSykedager, gjenståendeSykedager,
                    hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() },
                    Dokument.Type.Sykmelding.name
                ).asExecute
            )
        }
    }
}
