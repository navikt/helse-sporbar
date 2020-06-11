package no.nav.helse.sporbar

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class DokumentDao(val datasource: DataSource) {
    internal fun opprett(hendelseId: UUID, opprettet: LocalDateTime, dokumentId: UUID, type: Dokument.Type) {
        @Language("PostgreSQL")
        val hendelse = "INSERT INTO hendelse(hendelse_id, timestamp) VALUES(?,?) ON CONFLICT DO NOTHING"

        @Language("PostgreSQL")
        val dokument = "INSERT INTO dokument(dokument_id, type) VALUES(?,?) ON CONFLICT DO NOTHING"

        @Language("PostgreSQL")
        val hendelseDokument = """INSERT INTO hendelse_dokument(hendelse_id, dokument_id)
            VALUES(
                (SELECT h.id FROM hendelse h WHERE h.hendelse_id = ?),
                (SELECT d.id FROM dokument d WHERE d.dokument_id = ?))
            ON CONFLICT DO NOTHING"""
        sessionOf(datasource).use {
            it.transaction { session ->
                session.run(queryOf(hendelse, hendelseId, opprettet).asUpdate)
                session.run(queryOf(dokument, dokumentId, type.name).asUpdate)
                session.run(queryOf(hendelseDokument, hendelseId, dokumentId).asUpdate)
            }
        }
    }

    internal fun finn(hendelseIder: List<UUID>) = sessionOf(datasource).use { session ->
        @Language("PostgreSQL")
        val query = """SELECT distinct d.dokument_id, d.type
                       FROM hendelse h
                                INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                                INNER JOIN dokument d ON hd.dokument_id = d.id
                       WHERE h.hendelse_id = ANY ((?)::uuid[])"""
        session.run(
            queryOf(query, hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() })
                .map { row -> Dokument(row.uuid("dokument_id"), enumValueOf(row.string("type"))) }
                .asList
        )
    }

//    fun lagre(vedtaksperiodeId: UUID, fagsystemId: String) {
//        @Language("PostgreSQL")
//        val query = "INSERT INTO vedtak_utbetalingsref(vedtaksperiode_id, utbetalingsref) VALUES(?,?) ON CONFLICT DO NOTHING"
//        sessionOf(datasource).use { session ->
//            session.run(
//                queryOf(
//                    query,
//                    vedtaksperiodeId,
//                    fagsystemId
//                ).asUpdate
//            )
//        }
//    }
}

fun Row.uuid(columnLabel: String): UUID = UUID.fromString(string(columnLabel))
