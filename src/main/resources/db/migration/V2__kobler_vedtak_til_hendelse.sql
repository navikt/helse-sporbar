TRUNCATE utbetaling, oppdrag, vedtak_tilstand, vedtak_dokument, vedtak, vedtaksperiode, hendelse_dokument, dokument, hendelse;

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
