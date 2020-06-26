package no.nav.helse.sporbar

import java.util.UUID

class Dokument(val dokumentId: UUID, val type: Type) {
    enum class Type {
        Sykmelding, Søknad, Inntektsmelding
    }
}
