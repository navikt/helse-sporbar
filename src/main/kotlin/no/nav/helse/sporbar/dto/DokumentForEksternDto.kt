package no.nav.helse.sporbar.dto

import java.util.UUID

data class DokumentForEksternDto(
    val dokumentId: UUID,
    val type: Type,
) {
    enum class Type {
        Sykmelding,
        Søknad,
        Inntektsmelding,
    }
}
