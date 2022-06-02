CREATE TABLE trenger_inntektsmelding
(
    lopenummer         SERIAL PRIMARY KEY,
    id                 UUID UNIQUE NOT NULL,
    hendelse_id        UUID UNIQUE NOT NULL,
    hendelse_opprettet timestamp   NOT NULL,
    fodselsnummer      CHAR(11)    NOT NULL,
    orgnummer          CHAR(9)     NOT NULL,
    vedtaksperiode_id  UUID        NOT NULL,
    fom                DATE        NOT NULL,
    tom                DATE        NOT NULL,
    melding_innsatt    timestamp   NOT NULL,
    data               JSONB       NOT NULL
);

CREATE TABLE trenger_ikke_inntektsmelding
(
    lopenummer         SERIAL PRIMARY KEY,
    id                 UUID UNIQUE NOT NULL,
    hendelse_id        UUID UNIQUE NOT NULL,
    hendelse_opprettet timestamp   NOT NULL,
    fodselsnummer      CHAR(11)    NOT NULL,
    orgnummer          CHAR(9)     NOT NULL,
    vedtaksperiode_id  UUID        NOT NULL,
    fom                DATE        NOT NULL,
    tom                DATE        NOT NULL,
    melding_innsatt    timestamp   NOT NULL,
    data               JSONB       NOT NULL
);

CREATE INDEX trenger_inntektsmelding_vedtaksperiode_idx ON trenger_inntektsmelding (vedtaksperiode_id);
CREATE INDEX trenger_ikke_inntektsmelding_vedtaksperiode_idx ON trenger_ikke_inntektsmelding (vedtaksperiode_id);
