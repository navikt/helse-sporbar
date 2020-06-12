package no.nav.helse.sporbar

import java.util.*

internal class Dokument(internal val dokumentId: UUID, internal val type: Type) {
    internal enum class Type {
        Sykmelding, Søknad, Inntektsmelding
    }
}
