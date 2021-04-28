# Sporbar

![Bygg og deploy](https://github.com/navikt/helse-sporbar/workflows/Bygg%20og%20deploy/badge.svg)

Sporbar tolker interne hendelser og lager nye hendelser som publiseres for innsyn i saksbehandling.

## Topics

Data om sykepenger kan konsumeres ved å lese inn en eller begge disse topic-ene:

* `tbd.vedtak`
* `tbd.utbetaling`

### Tilgang

For å få tilgang til å lese fra topic-ene kan man henvende seg til team bømlo, for eksemepl via #team-bømlo-værsågod på
Slack. Vi trenger et applikasjonsnavn og et teamnavn i Azure AD, sammen med en begrunnelse om hvorfor dere trenger
tilgangen.

Informasjon om sykepenger må eventuelt sammenstilles fra de to topic-ene. Her er en beskrivelse av innholdet i
meldingene:

#### Meldinger på `tbd.vedtak` inneholder disse feltene:

| Felt | Forklaring |
| --- | --- |
| event | "vedtak|
| fødselsnummer | |
| aktørId | |
| organisasjonsnummer | |
| fom | |
| tom | |
| skjæringstidspunkt | Den første dagen i sykefraværet som går ut over arbeidsgiverperioden. Vilkårsprøving og fastsetting av sykepengegrunnlaget tar utgangspunkt i denne dagen |
| dokumenter | Liste av `dokumentId` og `type` til søknaden, sykmeldingen og eventuelt inntektsmeldingen |
| inntekt | Månedsinntekten som sykepengegrunnlaget beregnes ut fra, for den aktuelle arbeidsgiveren (**Eller for det aktuelle arbeidsforholdet??**) |
| sykepengegrunnlag | Inntekten som er lagt til grunn for sykepenger, på årsnivå, begrenset oppad til 6G. Inntekter fra flere arbeidsgivere kan inngå i sykepengegrunnlaget. |
| utbetalingId | Peker på en "utbetaling", det vil i praksis si en melding på utbetaling-topicen. |

#### Meldinger på `tbd.utbetaling` inneholder disse feltene:

| Felt | Forklaring | |
| --- | --- | --- |
| event|"utbetaling"|
| utbetalingId | NB, flere utbetalingId-er kan peke på samme fagsystemId |
| fødselsnummer | | |
| aktørId | | |
| organisasjonsnummer | | |
| fom | | |
| tom | | |
| forbrukteSykedager | Hvor mange virkesykedager er forbrukt totalt | |
| gjenståendeSykedager | Hvor mange sykedager det er igjen til maksdato | |
| automatiskBehandling | Ble utbetalingen utført automatisk? | |
| arbeidsgiverOppdrag | _Se forklaring i egen tabell_ | |
| type | En av `UTBETALING`, `ETTERUTBETALING`, `ANNULLERING` eller `REVURDERING`| |
| utbetalingsdager | En liste av: | |
| | **Felt** | **Forklaring** |
| | dato | |
| | type | _Se forklaring i egen tabell_ |
| | begrunnelse | Begrunnelse av årsak til avvising, kun inkludert for avviste dager |

#### Person- og arbeidsgiveroppdrag ser slik ut:

| Felt | Forklaring | |
| --- | --- | --- |
| mottaker | Organisasjonsnummer ved refusjon, eller fødselsnummer til mottakeren av utbetalingen | |
| fagområde | `SP` hvis utbetalingen er til søker, eller `SPREF` hvis det er refusjon til arbeidsgiver | |
| fagsystemId | ID i oppdragssystemet, for utbetalingsoppdraget. Oppdraget kan deles av flere "utbetalinger" | |
| nettoBeløp | Totalt beløp til utbetaling for hele oppdraget | |
| utbetalingslinjer | En liste av: | |
| | **Felt** | **Forklaring** |
| | fom | Fra-dato for denne kombinasjonen av dagsats og grad |
| | tom | Til-dato |
| | dagsats | Faktisk utbetalingsbeløp per dag, altså etter gradering |
| | totalbeløp | Utregning av dagsats ganger antall stønadsdager |
| | grad | Sykdomsgrad per dag |
| | stønadsdager | Antall virkedager mellom FOM og TOM. Helligdager er inkludert, men helgedager er ikke |

#### Dagtypene:

| Felt | Forklaring |
| --- | --- |
| NavDag | Utbetalingsdag fra Nav |
| NavHelgDag | Ingen utbetaling grunnet helg, men registrert syk |
| ArbeidsgiverperiodeDag | Beregnet at arbeidsgiver dekker sykepengeutbetaling |
| Arbeidsdag | Arbeidstaker var ikke syk denne dagen |
| Fridag | Arbeidstaker hadde ikke arbeidsdag denne dagen |
| AvvistDag | Arbeidstaker hadde ikke rett til sykepenger denne dagen |
| ForeldetDag | Dagen ligger for langt tilbake i tid til at man kan få sykepenger for den |
| UkjentDag | Vi har ikke mottatt informasjon om denne dagen, så den regnes som en arbeidsdag |

#### Begrunnelser (ikke implementert ennå):

| Kode |
| --- |
| SykepengedagerOppbrukt |
| MinimumInntekt |
| EgenmeldingUtenforArbeidsgiverperiode |
| MinimumSykdomsgrad |
| EtterDødsdato |
| ManglerOpptjening |
| ManglerMedlemskap |

Spør om noe er uklart :-)

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #område-helse.
