ALTER TABLE vedtak ADD COLUMN organisasjonsnummer VARCHAR;

UPDATE vedtak v SET organisasjonsnummer = o.mottaker FROM oppdrag o WHERE v.id = o.vedtak_id;

ALTER TABLE vedtak ALTER COLUMN organisasjonsnummer SET NOT NULL;
