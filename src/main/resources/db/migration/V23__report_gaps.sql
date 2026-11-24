-- I buchi nella serie dei report, anche sui dati gia' raccolti ([P §5.4], PIANO_METROLOGIA 2.4).
--
-- Il flag GAP lo calcola ReportQualityService su ogni report nuovo. I report arrivati prima
-- della V20 non hanno flag (quality_flags NULL, che vuol dire «non lo sappiamo»), ma i frame
-- ricevuti ci sono tutti in uplink_frame (un anno di ritenzione): questa vista ricava i buchi da
-- li', con la stessa regola del codice, cosi' la domanda «manca qualcosa?» ha una risposta anche
-- per il passato.
--
-- La regola: fra un report letto e il precedente letto della stessa CU sono passati piu' di 1,5
-- periodi di trasmissione. Il periodo e' quello configurato OGGI sulla CU, risolto con la scala
-- del dizionario di protocollo pubblicato: se il periodo e' cambiato nel tempo, i buchi di prima
-- del cambio vanno letti con prudenza (expected_seconds lo dice).

CREATE OR REPLACE VIEW grafana.report_gaps AS
WITH reports AS (
    SELECT f.control_unit_id,
           f.received_at,
           lag(f.received_at) OVER (PARTITION BY f.control_unit_id ORDER BY f.received_at) AS previous_at
    FROM public.uplink_frame f
    WHERE f.fport = 48                                   -- 0x30, il report
      AND f.status IN ('DECODED', 'DECODED_WITH_SNAPSHOT')
      AND f.control_unit_id IS NOT NULL
),
dictionary AS (
    SELECT t.content
    FROM public.template t
    WHERE t.kind = 'PROTOCOL' AND t.status <> 'REVOKED'
    ORDER BY t.major DESC, t.minor DESC, t.patch DESC
    LIMIT 1
),
expected AS (
    SELECT cu.id AS control_unit_id,
           cu.transmission_interval,
           (SELECT (seg ->> 'base')::double precision
                   + (cu.transmission_interval - (seg ->> 'from')::int) * (seg ->> 'step')::double precision
            FROM dictionary d,
                 jsonb_array_elements(d.content -> 'periods' -> 'transmission' -> 'segments') seg
            WHERE cu.transmission_interval BETWEEN (seg ->> 'from')::int AND (seg ->> 'to')::int
            LIMIT 1) AS seconds
    FROM public.control_unit cu
)
SELECT r.control_unit_id,
       r.received_at                                             AS "time",
       r.previous_at,
       extract(epoch FROM (r.received_at - r.previous_at))       AS gap_seconds,
       e.seconds                                                 AS expected_seconds,
       -- Quanti report mancano, a stima: l'intervallo diviso per il periodo, meno quello arrivato.
       CASE WHEN e.seconds > 0
            THEN greatest(round(extract(epoch FROM (r.received_at - r.previous_at)) / e.seconds) - 1, 0)
       END                                                       AS missing_reports,
       e.seconds > 0
           AND extract(epoch FROM (r.received_at - r.previous_at)) > 1.5 * e.seconds AS is_gap
FROM reports r
JOIN expected e ON e.control_unit_id = r.control_unit_id
WHERE r.previous_at IS NOT NULL;

COMMENT ON VIEW grafana.report_gaps IS
    'Distanza fra report letti consecutivi di una CU, con la regola del flag GAP (1,5 periodi di trasmissione): vale anche per i report precedenti alla V20';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grafana_ro') THEN
        GRANT USAGE ON SCHEMA grafana TO grafana_ro;
        GRANT SELECT ON ALL TABLES IN SCHEMA grafana TO grafana_ro;
    END IF;
END $$;
