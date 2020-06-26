package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode

data class Melding(val type: Type, val data: JsonNode) {
    enum class Type {
        Vedtak, Behandlingstilstand
    }
}
