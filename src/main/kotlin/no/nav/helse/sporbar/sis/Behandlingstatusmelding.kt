package no.nav.helse.sporbar.sis

import com.fasterxml.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.sporbar.Dokument
import no.nav.helse.sporbar.DokumentDao

data class Behandlingstatusmelding(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val tidspunkt: OffsetDateTime,
    val status: Behandlingstatustype,
    val eksterneSøknadIder: Set<UUID>
) {
    val versjon = "2.0.0"

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

        internal fun DokumentDao.eksterneSøknadIder(interneHendelseIder: List<UUID>) =
            finn(interneHendelseIder).filter { it.type == Dokument.Type.Søknad }.map { it.dokumentId }.toSet().takeUnless { it.isEmpty() }
    }
}
