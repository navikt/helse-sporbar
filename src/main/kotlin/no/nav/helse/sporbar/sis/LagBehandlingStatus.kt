package no.nav.helse.sporbar.sis

import java.time.OffsetDateTime
import java.util.UUID
import org.intellij.lang.annotations.Language

fun lagBehandlingStatus(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, status: String): String {
    @Language("JSON")
    val melding = """{
  "vedtaksperiodeId": "$vedtaksperiodeId",
  "behandlingId": "$behandlingId",
  "tidspunkt": "$tidspunkt",
  "status": "$status"
}"""
    return melding
}