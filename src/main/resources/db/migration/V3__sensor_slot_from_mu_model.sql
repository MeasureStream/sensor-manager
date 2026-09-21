-- Slot materializzati dal modello di MU (passo 11).

-- L'asse dello slot, per i modelli che istanziano piu' volte lo stesso template di sensore
-- (i tre assi di un IMU). Null per tutti i sensori esistenti, che non hanno canali.
ALTER TABLE public.sensor ADD COLUMN channel varchar(8);

-- Gli indici degli slot partono da 0, come le posizioni sul filo: il vecchio codice li
-- creava da 1. L'ordine relativo non cambia, quindi il formato dei comandi e dei report
-- resta identico; cambia solo il numero mostrato e quello usato nei confronti.
-- Si riallinea solo chi parte da 1, cosi' rieseguire la migrazione non sposta niente.
UPDATE public.sensor s
SET sensor_index = s.sensor_index - 1
WHERE s.mu_id IN (
    SELECT mu_id
    FROM public.sensor
    WHERE mu_id IS NOT NULL
    GROUP BY mu_id
    HAVING MIN(sensor_index) = 1
);
