-- Ogni campione dice con quale taratura e' stato convertito, quanto vale la sua incertezza,
-- a quale finestra di tempo si riferisce e che cosa c'e' da sapere sulla sua qualita'
-- (PIANO_METROLOGIA 1.14, 2.3, 2.4; specifica § 4.2 e § 4.3).
--
-- Migrazione additiva: nessuna riga esistente viene toccata. Sullo storico le colonne
-- restano NULL, che vuol dire «non lo sappiamo», non «zero»: un'incertezza nulla o un
-- insieme di flag vuoto sarebbero affermazioni false.

ALTER TABLE public.metric_sample
    -- La taratura dell'esemplare usata; NULL = formula nominale del modello.
    ADD COLUMN calibration_id bigint,
    -- Incertezza tipo (k = 1) del valore. L'estesa si calcola alla lettura, con k = 2.
    ADD COLUMN u_std          double precision,
    -- I contributi: taratura, lettura, tipo A, deriva, influenze. Spiegano un valore.
    ADD COLUMN u_components   jsonb,
    -- Bit di QualityFlags: CONVERTED, CAL_INSTANCE, CAL_EXPIRED, OUT_OF_CAL_RANGE, GAP,
    -- SNAPSHOT, TIME_UNSYNC, NO_AGE, CONFIG_MISMATCH.
    ADD COLUMN quality_flags  integer,
    -- La finestra di raccolta delle statistiche. Senza byte di eta' nel report la fine e'
    -- l'istante di ricezione, e il flag NO_AGE lo dichiara.
    ADD COLUMN window_start   timestamptz,
    ADD COLUMN window_end     timestamptz,
    ADD CONSTRAINT metric_sample_calibration_fk
        FOREIGN KEY (calibration_id) REFERENCES public.active_calibration(id) ON DELETE SET NULL;

CREATE INDEX idx_metric_sample_sensor_window_end
    ON public.metric_sample (sensor_id, window_end);

-- Le colonne della taratura di un sensore, ormai deprecate: nessuno le legge per convertire
-- (la conversione usa il template o la taratura attiva). Si tolgono nella fase 7.
COMMENT ON COLUMN public.sensor.coeffa IS 'Deprecata: sostituita da active_calibration. Si toglie nella fase 7.';
COMMENT ON COLUMN public.sensor.coeffb IS 'Deprecata: sostituita da active_calibration. Si toglie nella fase 7.';
COMMENT ON COLUMN public.sensor.coeffc IS 'Deprecata: sostituita da active_calibration. Si toglie nella fase 7.';
COMMENT ON COLUMN public.sensor.coeffd IS 'Deprecata: sostituita da active_calibration. Si toglie nella fase 7.';
