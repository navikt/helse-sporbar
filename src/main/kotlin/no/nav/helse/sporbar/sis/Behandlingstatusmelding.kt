package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

data class Behandlingstatusmelding(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val tidspunkt: OffsetDateTime,
    val status: Behandlingstatustype,
    val eksterneSøknadIder: Set<UUID>
) {
    val versjon = "2.0.1"

    enum class Behandlingstatustype {
        OPPRETTET,
        VENTER_PÅ_ARBEIDSGIVER,
        VENTER_PÅ_ANNEN_PERIODE,
        VENTER_PÅ_SAKSBEHANDLER,
        FERDIG,
        BEHANDLES_UTENFOR_SPEIL
    }

    internal companion object {
        internal fun behandlingOpprettet(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, eksterneSøknadIder: Set<UUID>) =
            Behandlingstatusmelding(vedtaksperiodeId, behandlingId, tidspunkt, Behandlingstatustype.OPPRETTET, eksterneSøknadIder)
        internal fun behandlingstatus(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, status: Behandlingstatustype, eksterneSøknadIder: Set<UUID> = emptySet()) =
            Behandlingstatusmelding(vedtaksperiodeId, behandlingId, tidspunkt, status, eksterneSøknadIder)
        private val Oslo = ZoneId.of("Europe/Oslo")
        internal fun JsonNode.asOffsetDateTime() = asLocalDateTime().atZone(Oslo).toOffsetDateTime()
    }
}
