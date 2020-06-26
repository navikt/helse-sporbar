DELETE
FROM utbetaling;
DELETE
FROM oppdrag;
DELETE
FROM vedtak_tilstand;
DELETE
FROM vedtak_dokument;
DELETE
FROM vedtak;
DELETE
FROM vedtaksperiode;
DELETE
FROM hendelse_dokument;
DELETE
FROM dokument;
DELETE
FROM hendelse;

ALTER TABLE vedtak
    DROP COLUMN vedtaksperiode_id;

DROP TABLE vedtak_dokument;
CREATE TABLE vedtaksperiode_hendelse
(
    vedtaksperiode_id INTEGER references vedtaksperiode (id),
    hendelse_id       INTEGER references hendelse (id),
    PRIMARY KEY (vedtaksperiode_id, hendelse_id)
);


CREATE TABLE vedtak_hendelse
(
    vedtak_id   INTEGER references vedtak (id),
    hendelse_id INTEGER references hendelse (id),
    PRIMARY KEY (vedtak_id, hendelse_id)
);
