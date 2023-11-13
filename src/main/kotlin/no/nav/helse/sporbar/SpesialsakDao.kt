package no.nav.helse.sporbar

import java.util.UUID
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

internal class SpesialsakDao(private val dataSource: DataSource) {
    internal fun spesialsak(vedtaksperiodeId: UUID): Boolean {
        @Language("PostgreSQL")
        val query = "SELECT true FROM spesialsak WHERE vedtaksperiode_id = :vedtaksperiode_id"
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(query, mapOf("vedtaksperiode_id" to vedtaksperiodeId)).map { it.boolean(1) }.asSingle) ?: false
        }
    }

    internal fun slett(vedtaksperiodeId: UUID) {
        @Language("PostgreSQL")
        val query = "DELETE FROM spesialsak WHERE vedtaksperiode_id = :vedtaksperiode_id"
        sessionOf(dataSource).use { session ->
            session.run(queryOf(query, mapOf("vedtaksperiode_id" to vedtaksperiodeId)).asUpdate)
        }
    }
}