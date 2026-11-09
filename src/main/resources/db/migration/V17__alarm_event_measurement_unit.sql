-- Allarmi ed eventi sanno da quale MU vengono.
--
-- Oggi la risposta si ricostruisce per vie traverse: dal sensore, che pero' e' nullo proprio
-- per gli eventi di livello MU (`sensor_index` 0xFF), oppure dalla coppia
-- (`control_unit_id`, `source`), che e' il Local ID **di allora**. Basta che la MU cambi
-- posizione o passi a un'altra CU e la stessa domanda, fatta domani, risponde il falso.
--
-- Con un riferimento diretto la storia di una MU resta sua attraverso gli spostamenti, ed e'
-- una lettura indicizzata invece di una ricostruzione.
--
-- Il riempimento retroattivo passa dal sensore, quindi copre le righe di canale. Quelle di
-- livello MU gia' scritte restano senza: il dato per dedurle non c'e', e inventarlo sarebbe
-- peggio del vuoto.

ALTER TABLE public.device_event ADD COLUMN measurement_unit_id bigint;
ALTER TABLE public.sensor_alarm ADD COLUMN measurement_unit_id bigint;

ALTER TABLE public.device_event
    ADD CONSTRAINT device_event_measurement_unit_fk
        FOREIGN KEY (measurement_unit_id) REFERENCES public.measurement_unit(id) ON DELETE SET NULL;
ALTER TABLE public.sensor_alarm
    ADD CONSTRAINT sensor_alarm_measurement_unit_fk
        FOREIGN KEY (measurement_unit_id) REFERENCES public.measurement_unit(id) ON DELETE SET NULL;

UPDATE public.device_event e
   SET measurement_unit_id = s.mu_id
  FROM public.sensor s
 WHERE s.id = e.sensor_id AND s.mu_id IS NOT NULL;

UPDATE public.sensor_alarm a
   SET measurement_unit_id = s.mu_id
  FROM public.sensor s
 WHERE s.id = a.sensor_id AND s.mu_id IS NOT NULL;

CREATE INDEX device_event_measurement_unit_idx
    ON public.device_event (measurement_unit_id, received_at DESC);
CREATE INDEX sensor_alarm_measurement_unit_idx
    ON public.sensor_alarm (measurement_unit_id, received_at DESC);
