-- Il sensore sa quando ha misurato l'ultima volta.
--
-- I grafici partivano da «adesso meno un'ora». Su un sensore che tace da giorni — spento,
-- staccato, o su una CU che trasmette ogni sei ore — quella finestra inquadra il vuoto: il
-- pannello dice «No data» mentre le misure esistono, poco piu' indietro.
--
-- La colonna si scrive nella stessa transazione che aggiorna il valore in pagina, quindi non
-- costa una query in scrittura; in lettura e' gia' dentro la riga del sensore, e la finestra
-- predefinita diventa «ultima misura meno un'ora → ultima misura» senza interrogare
-- metric_sample a ogni apertura di una scheda.
--
-- Il riempimento retroattivo guarda dentro metric_sample una volta sola, qui. Senza, i
-- grafici resterebbero ancorati ad «adesso» fino al primo report successivo di ogni sensore.

ALTER TABLE public.sensor ADD COLUMN last_value_at timestamptz;

UPDATE public.sensor s
   SET last_value_at = m.ultimo
  FROM (
        SELECT sensor_id, max("timestamp") AS ultimo
          FROM public.metric_sample
         GROUP BY sensor_id
       ) m
 WHERE m.sensor_id = s.id;
