create index if not exists inntektsmelding_usendte_idx on inntektsmelding_status (lopenummer)
    where melding_publisert is null and melding_ignorert is null;