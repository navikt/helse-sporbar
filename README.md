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

| Felt                                        | Forklaring                                                                                                                                                                                                                                        |
|---------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| fødselsnummer                               |                                                                                                                                                                                                                                                   |
| aktørId                                     |                                                                                                                                                                                                                                                   |
| organisasjonsnummer                         |                                                                                                                                                                                                                                                   |
| fom                                         |                                                                                                                                                                                                                                                   |
| tom                                         |                                                                                                                                                                                                                                                   |
| skjæringstidspunkt                          | Den første dagen i sykefraværet som går ut over arbeidsgiverperioden. Med andre ord dagen etter siste dag søker var på jobb før gjeldende sykefraværsperiode. Vilkårsprøving og fastsetting av sykepengegrunnlaget tar utgangspunkt i denne dagen |
| dokumenter                                  | Liste av `dokumentId` og `type` til søknaden, sykmeldingen og eventuelt inntektsmeldingen                                                                                                                                                         |
| inntekt                                     | Månedsinntekten som sykepengegrunnlaget beregnes ut fra, for den aktuelle arbeidsgiveren (**Eller for det                                                                                                                                         |
| aktuelle arbeidsforholdet??**)              |                                                                                                                                                                                                                                                   |
| sykepengegrunnlag                           | Inntekten som er lagt til grunn for sykepenger, på årsnivå, begrenset oppad til 6G. Inntekter fra flere arbeidsgivere kan inngå i sykepengegrunnlaget.                                                                                            |
| grunnlagForSykepengegrunnlag                | Den samlede årlige inntekten før evt. begrensning                                                                                                                                                                                                 |
| grunnlagForSykepengegrunnlagPerArbeidsgiver | Et objekt med orgnummer -> årlig inntekt per arbeidsgiver `{"123456789": 500000.0, "987654321": 700000.0}`                                                                                                                                        |
| begrensning                                 | Om sykepengegrunnlaget er 6G begrenset. En av disse: `ER_6G_BEGRENSET`, `ER_IKKE_6G_BEGRENSET`, `VURDERT_I_INFOTRYGD` og `VET_IKKE`                                                                                                               |
| utbetalingId                                | Peker på en "utbetaling", det vil i praksis si en melding på utbetaling-topicen.                                                                                                                                                                  |

#### Meldinger på `tbd.utbetaling` inneholder disse feltene:

| Felt                 | Forklaring                                                               |                                                                    |
|----------------------|--------------------------------------------------------------------------|--------------------------------------------------------------------|
| event                | "utbetaling_utbetalt" eller "utbetaling_uten_utbetaling"                 |
| utbetalingId         | NB, flere utbetalingId-er kan peke på samme fagsystemId                  |
| fødselsnummer        |                                                                          |                                                                    |
| aktørId              |                                                                          |                                                                    |
| organisasjonsnummer  |                                                                          |                                                                    |
| fom                  |                                                                          |                                                                    |
| tom                  |                                                                          |                                                                    |
| forbrukteSykedager   | Hvor mange virkesykedager er forbrukt totalt                             |                                                                    |
| gjenståendeSykedager | Hvor mange sykedager det er igjen til maksdato                           |                                                                    |
| automatiskBehandling | Ble utbetalingen utført automatisk?                                      |                                                                    |
| arbeidsgiverOppdrag  | _Se forklaring i egen tabell_                                            |                                                                    |
| type                 | En av `UTBETALING`, `ETTERUTBETALING`, `ANNULLERING` eller `REVURDERING` |                                                                    |
| utbetalingsdager     | En liste av:                                                             |                                                                    |
|                      | **Felt**                                                                 | **Forklaring**                                                     |
|                      | dato                                                                     |                                                                    |
|                      | type                                                                     | _Se forklaring i egen tabell_                                      |
|                      | begrunnelse                                                              | Begrunnelse av årsak til avvising, kun inkludert for avviste dager |

#### Arbeidsgiveroppdrag ser slik ut:

| Felt              | Forklaring                                                                                   |                                                                                                         |
|-------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| mottaker          | Organisasjonsnummer ved refusjon, eller fødselsnummer til mottakeren av utbetalingen         |                                                                                                         |
| fagområde         | `SP` hvis utbetalingen er til søker, eller `SPREF` hvis det er refusjon til arbeidsgiver     |                                                                                                         |
| fagsystemId       | ID i oppdragssystemet, for utbetalingsoppdraget. Oppdraget kan deles av flere "utbetalinger" |                                                                                                         |
| nettoBeløp        | Totalt beløp til utbetaling for hele oppdraget                                               |                                                                                                         |
| utbetalingslinjer | En liste, der hvert element inneholder følgende felter:                                      |                                                                                                         |
|                   | **Felt**                                                                                     | **Forklaring**                                                                                          |
|                   | fom                                                                                          | Fra-dato for denne kombinasjonen av dagsats og grad                                                     |
|                   | tom                                                                                          | Til-dato                                                                                                |
|                   | dagsats                                                                                      | Faktisk utbetalingsbeløp per dag, altså etter gradering                                                 |
|                   | totalbeløp                                                                                   | Utregning av dagsats ganger antall stønadsdager                                                         |
|                   | grad                                                                                         | Sykdomsgrad per dag                                                                                     |
|                   | stønadsdager                                                                                 | Antall dager mellom FOM og TOM med utbetaling fra Nav. Helligdager er inkludert, men helgedager er ikke |

OBS: på `aapen-helse-sporbar` inneholdt beløp-feltet "beløp til utbetaling per dag etter gradering", dette beløpet
ligger nå i dagsats-feltet.

#### Dagtypene:

| Felt                   | Forklaring                                                                      |
|------------------------|---------------------------------------------------------------------------------|
| NavDag                 | Utbetalingsdag fra Nav                                                          |
| NavHelgDag             | Ingen utbetaling grunnet helg, men registrert syk                               |
| ArbeidsgiverperiodeDag | Beregnet at arbeidsgiver dekker sykepengeutbetaling                             |
| Arbeidsdag             | Arbeidstaker var på jobb                                                        |
| Fridag                 | Arbeidstaker hadde fri                                                          |
| Feriedag               | Arbeidstaker hadde ferie                                                        |
| Permisjonsdag          | Arbeidstaker hadde permisjon                                                    |
| AvvistDag              | Arbeidstaker hadde ikke rett til sykepenger                                     |
| ForeldetDag            | Dagen ligger for langt tilbake i tid til at man kan få sykepenger for den       |
| UkjentDag              | Vi har ikke mottatt informasjon om denne dagen, så den regnes som en arbeidsdag |

#### Begrunnelser:

| Kode                                  |
|---------------------------------------|
| SykepengedagerOppbrukt                |
| SykepengedagerOppbruktOver67          |
| MinimumInntekt                        |
| MinimumInntektOver67                  |
| EgenmeldingUtenforArbeidsgiverperiode |
| MinimumSykdomsgrad                    |
| EtterDødsdato                         |
| ManglerOpptjening                     |
| ManglerMedlemskap                     |
| Over70                                |

Spør om noe er uklart :-)


## Oppgradering av gradle wrapper
Finn nyeste versjon av gradle her: https://gradle.org/releases/

```./gradlew wrapper --gradle-version $gradleVersjon```

Husk å oppdatere gradle versjonen i build.gradle.kts filen
```val gradlewVersion = "$gradleVersjon"```

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod
