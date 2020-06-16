package no.nav.helse.sporbar

internal data class VedtaksperiodeDto(
    val fnr: String,
    val orgnummer: String,
    val vedtak: Vedtak?,
    val dokumenter: List<Dokument>,
    val manglendeDokumenter: List<Dokument.Type>,
    val tilstand: TilstandDto
) {
    internal enum class TilstandDto {
        AvventerDokumentasjon, UnderBehandling
    }
}
