CREATE TABLE hendelse(
    id SERIAL PRIMARY KEY,
    hendelse_id UUID UNIQUE NOT NULL,
    timestamp timestamp NOT NULL
);

CREATE TABLE dokument
(
    id SERIAL PRIMARY KEY,
    dokument_id UUID UNIQUE NOT NULL,
    type        VARCHAR NOT NULL
);

CREATE TABLE hendelse_dokument(
    hendelse_id references hendelse(id),
    dokument_id references dokument(id),
    PRIMARY KEY (hendelse_id, dokument_id)
);

CREATE TABLE vedtak_dokument(
    vedtak_id references vedtak(id),
    dokument_id references dokument(id),
    PRIMARY KEY (vedtak_id, dokument_id)
);

CREATE TABLE vedtaksperiode
(
    id                    SERIAL PRIMARY KEY,
    vedtaksperiode_id UUID NOT NULL,
    fodselsnummer         CHAR(11)  NOT NULL,
    orgnummer             CHAR(9)   NOT NULL
);
CREATE INDEX vedtaksperiode_fodselsnummer_idx ON vedtaksperiode(fodselsnummer);

CREATE TABLE vedtak(
    id SERIAL PRIMARY KEY,
    fom                   DATE      NOT NULL,
    tom                   DATE      NOT NULL,
    forbrukte_sykedager   INTEGER   NOT NULL,
    gjenstaende_sykedager INTEGER   NOT NULL,
    vedtaksperiode_id references vedtaksperiode(id)
);
CREATE INDEX vedtak_vedtaksperiode_idx ON vedtak(vedtaksperiode_id)

CREATE TABLE vedtak_tilstand
(
    id        SERIAL PRIMARY KEY,
    vedtaksperiode_id INTEGER NOT NULL REFERENCES vedtaksperiode(id),
    sist_endret TIMESTAMP NOT NULL,
    tilstand varchar NOT NULL,
);
CREATE INDEX vedtak_tilstand_vedtaksperiode_idx ON vedtak_tilstand(vedtaksperiode_id)

CREATE TABLE oppdrag
(
    id          SERIAL PRIMARY KEY,
    vedtak_id   INTEGER NOT NULL REFERENCES vedtak (id),
    mottaker    VARCHAR,
    fagområde   VARCHAR,
    fagsystemId VARCHAR,
    totalbeløp  INTEGER
);
CREATE INDEX oppdrag_vedtak_id_idx ON oppdrag(vedtak_id)

CREATE TABLE utbetaling
(
    id         SERIAL PRIMARY KEY,
    oppdrag_id INTEGER NOT NULL REFERENCES oppdrag (id),
    fom        DATE    NOT NULL,
    tom        DATE    NOT NULL,
    dagsats    INTEGER NOT NULL,
    grad       DECIMAL NOT NULL,
    belop      INTEGER NOT NULL,
    sykedager  INTEGER NOT NULL
);
CREATE INDEX utbetaling_oppdrag_idx ON utbetaling(oppdrag_id)
