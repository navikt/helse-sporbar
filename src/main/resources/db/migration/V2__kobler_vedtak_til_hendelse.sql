TRUNCATE utbetaling;
TRUNCATE oppdrag;
TRUNCATE vedtak_tilstand;
TRUNCATE vedtak_dokument;
TRUNCATE vedtak;
TRUNCATE vedtaksperiode;
TRUNCATE hendelse_dokument;
TRUNCATE dokument;
TRUNCATE hendelse;

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
