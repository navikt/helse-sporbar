CREATE TABLE vedtaksperiode_forkastet
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

CREATE INDEX vedtaksperiode_forkastet_idx ON vedtaksperiode_forkastet (vedtaksperiode_id);
