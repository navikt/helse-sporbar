package no.nav.helse.sporbar.sis

import java.util.UUID

interface SisPublisher {
    fun send(vedtaksperiodeId: UUID, melding: String)
}