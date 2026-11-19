-- I campioni per (MU, indice del sensore), per i pannelli dei punti di controllo
-- (PIANO_METROLOGIA 4.6).
--
-- Un punto di controllo e' sorvegliato da esemplari diversi nel tempo: quando una MU si
-- sostituisce, la serie continua con la MU nuova. metrology-service tiene le assegnazioni
-- (metrology.control_point_assignment, per EUID e indice); il pannello di Grafana le unisce a
-- questa vista, che espone ogni campione con l'EUID della sua MU invece dell'id interno del
-- sensore. Il tempo e' la fine della finestra di misura quando c'e' (fase 2), altrimenti
-- l'istante del campione.

CREATE OR REPLACE VIEW grafana.sample_series AS
SELECT mu.extended_id                                 AS mu_extended_id,
       s.sensor_index,
       m.metric,
       COALESCE(m.window_end, m."timestamp")           AS "time",
       m.value,
       m.converted,
       2 * m.u_std                                     AS incertezza_estesa,
       m.value - 2 * m.u_std                           AS banda_inf,
       m.value + 2 * m.u_std                           AS banda_sup,
       m.quality_flags                                 AS flag_qualita,
       m.calibration_id
FROM public.metric_sample m
JOIN public.sensor s            ON s.id = m.sensor_id
JOIN public.measurement_unit mu ON mu.id = s.mu_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grafana_ro') THEN
        GRANT USAGE ON SCHEMA grafana TO grafana_ro;
        GRANT SELECT ON ALL TABLES IN SCHEMA grafana TO grafana_ro;
    END IF;
END $$;
