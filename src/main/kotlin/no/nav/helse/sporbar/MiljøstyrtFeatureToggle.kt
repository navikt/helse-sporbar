package no.nav.helse.sporbar

internal class MiljøstyrtFeatureToggle(private val env: Map<String, String>) {
    internal fun annullering() = env.getOrDefault("ANNULLERING", "false").toBoolean()
}
