package no.nav.helse.sporbar

import java.util.*

internal class Dokument(private val dokumentId: UUID, private val type: Type) {
    internal enum class Type {
        Sykmelding, Søknad, Inntektsmelding
    }
}
