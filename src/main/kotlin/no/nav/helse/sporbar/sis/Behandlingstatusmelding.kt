package no.nav.helse.sporbar.sis

import java.time.OffsetDateTime
import java.util.UUID

data class Behandlingstatusmelding(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val tidspunkt: OffsetDateTime,
    val status: Behandlingstatustype,
    val eksternSøknadId: UUID? = null
) {
    val versjon = "1.0.0-beta"

    enum class Behandlingstatustype {
        OPPRETTET,
        VENTER_PÅ_ARBEIDSGIVER,
        VENTER_PÅ_SAKSBEHANDLER,
        FERDIG,
        BEHANDLES_UTENFOR_SPEIL
    }

    companion object {
        fun behandlingOpprettet(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, eksternSøknadId: UUID) =
            Behandlingstatusmelding(vedtaksperiodeId, behandlingId, tidspunkt, Behandlingstatustype.OPPRETTET, eksternSøknadId)
        fun behandlingstatus(vedtaksperiodeId: UUID, behandlingId: UUID, tidspunkt: OffsetDateTime, status: Behandlingstatustype) =
            Behandlingstatusmelding(vedtaksperiodeId, behandlingId, tidspunkt, status, null)
    }
}
