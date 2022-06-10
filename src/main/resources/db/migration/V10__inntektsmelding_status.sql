CREATE TABLE inntektsmelding_status
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
    melding_innsatt    timestamp   DEFAULT now(),
    melding_publisert  timestamp   DEFAULT NULL,
    status             VARCHAR     NOT NULL,
    data               JSONB       NOT NULL
);

CREATE INDEX inntektsmelding_status_vedtaksperiode_id_idx ON inntektsmelding_status (vedtaksperiode_id);
