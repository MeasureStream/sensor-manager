-- Le viste di Grafana imparano a dire l'unita' di misura, e smettono di chiamare varianza
-- una deviazione standard.
--
-- I pannelli di Grafana sono quattro in tutto e servono ogni sensore: quale sensore mostrino
-- lo decide la variabile sensor_id. Unita' e limiti degli assi stanno pero' nelle opzioni del
-- pannello, scritte a mano, e nessun parametro dell'URL puo' cambiarle: percio' un pannello
-- tarato sulla pressione mostra la temperatura fuori scala, cioe' non la mostra.
--
-- La trasformazione «Config from query results» di Grafana risolve proprio questo: prende
-- unita', minimo e massimo da una seconda query e li applica alla serie. Questa migrazione
-- fornisce quella query.

-- ---------------------------------------------------------------- scala per sensore

CREATE VIEW grafana.sensor_scale AS
SELECT s.id                                                      AS sensor_id,
       s.model_name,
       t.content ->> 'type'                                      AS grandezza,
       -- L'unita' come la dichiara il template, in notazione D-SI.
       t.content ->> 'unit'                                      AS dsi,
       -- La stessa unita' tradotta negli identificatori di Grafana. La traduzione vive qui e
       -- non nel template perche' e' una convenzione di un solo strumento, e quello strumento
       -- prima o poi esce di scena: il template resta metrologico.
       CASE t.content ->> 'unit'
           WHEN '\degreeCelsius'             THEN 'celsius'
           WHEN '\percent'                   THEN 'percent'
           WHEN '\millibar'                  THEN 'pressurembar'
           WHEN '\meter\per\second\squared'  THEN 'accMS2'
           WHEN '\degree\per\second'         THEN 'degree'
           ELSE 'none'
       END                                                       AS unit,
       -- Il campo di misura dichiarato dal modello. Attenzione: e' la CAPACITA' del sensore,
       -- non una finestra di lettura. La pressione dichiara 10..2000 mbar, e un asse cosi'
       -- ampio rende piatta una curva che vive attorno a 1013: come limiti dell'asse vanno
       -- usati solo dove sono anche una scala sensata, per esempio l'umidita' 0..100.
       (t.content -> 'ranges' -> 'phys' ->> 'min')::double precision AS min,
       (t.content -> 'ranges' -> 'phys' ->> 'max')::double precision AS max
FROM public.sensor s
-- LEFT JOIN: un sensore il cui template non e' nel registro resta nell'elenco con i campi a
-- NULL, e la trasformazione di Grafana semplicemente non applica nulla. Meglio di una riga
-- che sparisce senza spiegazione.
LEFT JOIN LATERAL (
    SELECT tp.content
    FROM public.template tp
    WHERE tp.kind = 'SENSOR'
      -- lower() su entrambi i lati: e' la forma su cui esiste l'indice del registro, ed e'
      -- anche quella che non si rompe se un template arriva con il nome scritto diversamente.
      AND lower(tp.model_name) = lower(s.model_name)
      AND tp.status <> 'REVOKED'
    ORDER BY tp.major DESC, tp.minor DESC, tp.patch DESC
    LIMIT 1
) t ON TRUE;

COMMENT ON VIEW grafana.sensor_scale IS
    'Unita e campo di misura per sensore, presi dal template pubblicato: alimenta la trasformazione «Config from query results» dei pannelli';

-- ------------------------------------------------- varianza e deviazione standard

-- La colonna si chiamava deviazione_standard ma conteneva la varianza: il server propaga
-- «varianza x derivata^2», quindi il numero e' in unita' al quadrato (gradi Celsius quadri,
-- millibar quadri). Sullo stesso grafico della media, o la schiaccia o si fa schiacciare.
--
-- Ora le due cose hanno ciascuna la propria colonna e ciascuna dice la verita'. La radice si
-- calcola qui e non nel server perche' e' una scelta di presentazione: il dato conservato
-- resta la varianza, che e' cio' che il dispositivo trasmette.
CREATE OR REPLACE VIEW grafana.measure_avg_std AS
SELECT m.sensor_id,
       m."timestamp"                                             AS "time",
       MAX(CASE WHEN m.metric = 'mean' THEN m.value END)         AS media,
       -- Il CASE, e non GREATEST, perche' GREATEST(NULL, 0) vale 0: una varianza assente
       -- diventerebbe una deviazione standard nulla, cioe' una misura inventata.
       CASE
           WHEN MAX(CASE WHEN m.metric = 'variance' THEN m.value END) >= 0
               THEN sqrt(MAX(CASE WHEN m.metric = 'variance' THEN m.value END))
       END                                                       AS deviazione_standard,
       MAX(CASE WHEN m.metric = 'variance' THEN m.value END)     AS varianza
FROM public.metric_sample m
WHERE m.metric IN ('mean', 'variance')
GROUP BY m.sensor_id, m."timestamp";

-- Il GRANT e' condizionato: l'utente in sola lettura di Grafana e' facoltativo.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grafana_ro') THEN
        GRANT USAGE ON SCHEMA grafana TO grafana_ro;
        GRANT SELECT ON ALL TABLES IN SCHEMA grafana TO grafana_ro;
    END IF;
END $$;
