-- Le viste di Grafana imparano l'incertezza (PIANO_METROLOGIA 2.7, specifica § 4.6).
--
-- Una media senza la sua incertezza e' un numero che sembra piu' preciso di quanto sia. La
-- banda ±U (k = 2) si aggiunge come colonne in coda alle viste esistenti: CREATE OR REPLACE
-- VIEW accetta solo colonne nuove in fondo, e cosi' i pannelli gia' configurati continuano a
-- leggere le loro senza accorgersi di niente. Per mostrare la banda basta aggiungere al
-- pannello le due serie banda_inf e banda_sup con un riempimento fra le due.
--
-- Sullo storico u_std e' NULL e la banda manca: non e' zero, e' «non calcolata».

CREATE OR REPLACE VIEW grafana.measure_avg_std AS
SELECT m.sensor_id,
       m."timestamp"                                             AS "time",
       MAX(CASE WHEN m.metric = 'mean' THEN m.value END)         AS media,
       CASE
           WHEN MAX(CASE WHEN m.metric = 'variance' THEN m.value END) >= 0
               THEN sqrt(MAX(CASE WHEN m.metric = 'variance' THEN m.value END))
       END                                                       AS deviazione_standard,
       MAX(CASE WHEN m.metric = 'variance' THEN m.value END)     AS varianza,
       -- Incertezza estesa della media, k = 2.
       2 * MAX(CASE WHEN m.metric = 'mean' THEN m.u_std END)     AS incertezza_estesa,
       MAX(CASE WHEN m.metric = 'mean' THEN m.value END)
           - 2 * MAX(CASE WHEN m.metric = 'mean' THEN m.u_std END) AS banda_inf,
       MAX(CASE WHEN m.metric = 'mean' THEN m.value END)
           + 2 * MAX(CASE WHEN m.metric = 'mean' THEN m.u_std END) AS banda_sup,
       -- I flag di qualita' della media, per colorare o filtrare i punti.
       MAX(CASE WHEN m.metric = 'mean' THEN m.quality_flags END) AS flag_qualita,
       -- Vero se la media e' stata convertita con la taratura dell'esemplare.
       bool_or(m.metric = 'mean' AND m.calibration_id IS NOT NULL) AS taratura_esemplare
FROM public.metric_sample m
WHERE m.metric IN ('mean', 'variance')
GROUP BY m.sensor_id, m."timestamp";

CREATE OR REPLACE VIEW grafana.measure_punctual AS
SELECT sensor_id,
       "timestamp"         AS "time",
       value               AS valore_puntuale,
       2 * u_std           AS incertezza_estesa,
       value - 2 * u_std   AS banda_inf,
       value + 2 * u_std   AS banda_sup,
       quality_flags       AS flag_qualita
FROM public.metric_sample
WHERE metric = 'punctual';

-- La taratura in vigore per sensore, per un pannello di stato o un'annotazione.
CREATE OR REPLACE VIEW grafana.sensor_calibration AS
SELECT s.id                  AS sensor_id,
       a.certificate_id,
       a.certificate_number,
       a.revision,
       a.valid_from,
       a.valid_until,
       a.calibrated_min,
       a.calibrated_max
FROM public.sensor s
JOIN public.measurement_unit mu ON mu.id = s.mu_id
JOIN LATERAL (
    SELECT ac.*
    FROM public.active_calibration ac
    WHERE ac.mu_extended_id = mu.extended_id
      AND ac.sensor_index = s.sensor_index
      AND ac.revoked_at IS NULL
      AND ac.valid_from <= now()
    ORDER BY ac.valid_from DESC
    LIMIT 1
) a ON TRUE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grafana_ro') THEN
        GRANT USAGE ON SCHEMA grafana TO grafana_ro;
        GRANT SELECT ON ALL TABLES IN SCHEMA grafana TO grafana_ro;
    END IF;
END $$;
