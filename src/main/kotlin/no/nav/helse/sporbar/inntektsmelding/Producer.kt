package no.nav.helse.sporbar.inntektsmelding

internal interface Producer {
    fun send(key: String, value: String)
}