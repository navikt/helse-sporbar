# Sporbar

![Bygg og deploy](https://github.com/navikt/helse-sporbar/workflows/Bygg%20og%20deploy/badge.svg)

Sporbar tolker interne hendelser og lager nye hendelser som publiseres for innsyn i saksbehandling.

## Topics

Data om sykepenger kan konsumeres ved å lese inn en eller begge disse topic-ene:

* `tbd.vedtak`
* `tbd.utbetaling`
* `tbd.inntektsmeldingstatus`

### Tilgang

For å få tilgang til å lese fra topic-ene kan man henvende seg til team bømlo, for eksemepl via #team-bømlo-værsågod på
Slack. Vi trenger et applikasjonsnavn og et teamnavn i Azure AD, sammen med en begrunnelse om hvorfor dere trenger
tilgangen.

Informasjon om sykepenger må eventuelt sammenstilles fra de to topic-ene. Her er en beskrivelse av innholdet i
meldingene:

### Format på meldingene

`OBS:` Om det nylig er gjort endringer på skjemaet kan det ta noen minutter før det oppdaterer seg på linkene ettersom Github cacher `raw`-domenet. Om man laster skjemaet fra commit hash forcer en oppdatert versjon. Tilsvarende kan man gjøre om man faktisk ønsker å se en eldre versjon av meldingen.

#### tbd.vedtak

JSON Schema for vedtak ([view](https://json-schema.app/view/%23?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnavikt%2Fhelse-sporbar%2Fmaster%2Fsrc%2Ftest%2Fresources%2Fjson-schema%2Ftbd.vedtak_v1.1.0.json) | [raw](src/test/resources/json-schema/tbd.vedtak_v1.1.0.json))

#### tbd.utbetaling

JSON Schema for utbetaling ([view](https://json-schema.app/view/%23?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnavikt%2Fhelse-sporbar%2Fmaster%2Fsrc%2Ftest%2Fresources%2Fjson-schema%2Ftbd.utbetaling.json) | [raw](src/test/resources/json-schema/tbd.utbetaling.json))

JSON Schema for annullering ([view](https://json-schema.app/view/%23?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnavikt%2Fhelse-sporbar%2Fmaster%2Fsrc%2Ftest%2Fresources%2Fjson-schema%2Ftbd.utbetaling__annullering.json) | [raw](src/test/resources/json-schema/tbd.utbetaling__annullering.json)) (gjelder også for annulleringer på `aapen-helse-sporbar`)

`OBS:` på `aapen-helse-sporbar` inneholdt beløp-feltet "beløp til utbetaling per dag etter gradering", dette beløpet ligger nå i dagsats-feltet.

#### tbd.inntektsmeldingstatus

JSON Schema for inntektsmeldingstatus ([view](https://json-schema.app/view/%23?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnavikt%2Fhelse-sporbar%2Fmaster%2Fsrc%2Ftest%2Fresources%2Fjson-schema%2Ftbd.inntektsmeldingstatus.json) | [raw](src/test/resources/json-schema/tbd.inntektsmeldingstatus.json))

## Oppgradering av gradle wrapper
Finn nyeste versjon av gradle her: https://gradle.org/releases/

```./gradlew wrapper --gradle-version $gradleVersjon```

Husk å oppdatere gradle versjonen i build.gradle.kts filen
```val gradlewVersion = "$gradleVersjon"```

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod
