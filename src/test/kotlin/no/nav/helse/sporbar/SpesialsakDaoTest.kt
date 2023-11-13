package no.nav.helse.sporbar

import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpesialsakDaoTest {
    private val dao = SpesialsakDao(TestDatabase.dataSource)

    @Test
    fun `er spesialsak`() {
        val vedtaksperiodeId = UUID.randomUUID()
        opprettSpesialsak(vedtaksperiodeId)
        assertTrue(dao.spesialsak(vedtaksperiodeId))
    }

    @Test
    fun `kan slette periode fra spesialsak-tabellen`() {
        val vedtaksperiodeId = UUID.randomUUID()
        opprettSpesialsak(vedtaksperiodeId)
        assertTrue(dao.spesialsak(vedtaksperiodeId))
        dao.slett(vedtaksperiodeId)
        assertFalse(dao.spesialsak(vedtaksperiodeId))
    }

    private fun opprettSpesialsak(vedtaksperiodeId: UUID) {
        @Language("PostgreSQL")
        val query = "INSERT INTO spesialsak (vedtaksperiode_id) VALUES (:vedtaksperiode_id)"
        sessionOf(TestDatabase.dataSource).use {
            it.run(queryOf(query, mapOf("vedtaksperiode_id" to vedtaksperiodeId)).asUpdate)
        }
    }
}