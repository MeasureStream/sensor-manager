-- L'origine di un evento: chi l'ha osservato.
--
-- Finora `device_event` conteneva solo cio' che il dispositivo dichiarava con la 0xA2. Un
-- fatto che il server vedeva da se' — la topologia delle MU cambiata, un comando che non
-- risulta applicato — non aveva nessun posto dove essere registrato: compariva come stato
-- corrente, spariva quando lo stato cambiava, e nella scheda «Eventi e diagnostica» restava
-- scritto «nessun evento registrato» mentre in alto lampeggiava un avviso. Due viste della
-- stessa cosa che non si parlavano.
--
-- Tre origini, perche' sono tre canali diversi e l'utente ha diritto di sapere quale:
--   device  l'evento e' arrivato sulla FPort 0xA2, dichiarato dal dispositivo;
--   status  il server l'ha letto nella parola di stato del poll, sul fronte di salita di un
--           bit che il dizionario classifica come `event` (reset, topologia cambiata,
--           configurazione rifiutata, tamper);
--   server  l'ha osservato il server confrontando cio' che ha inviato con cio' che la CU
--           dichiara: nessun bit lo racconta, ma e' un fatto accaduto.
--
-- `code` resta il codice nel suo spazio: per `status` e' il numero del bit, per `device` il
-- codice della tabella eventi, per `server` un piccolo elenco interno. Non si confondono
-- perche' l'origine li separa.

ALTER TABLE public.device_event
    ADD COLUMN origin varchar(16) NOT NULL DEFAULT 'device';

COMMENT ON COLUMN public.device_event.origin IS
    'Chi ha osservato l''evento: device (0xA2), status (parola di stato del poll), server';

-- L'indice serve a una domanda sola, fatta a ogni poll: «c'e' gia' un evento aperto con
-- questo codice per questa CU?». Parziale, perche' gli eventi presi in carico non si
-- interrogano mai cosi' e la storia puo' diventare lunga.
CREATE INDEX IF NOT EXISTS device_event_open_idx
    ON public.device_event (control_unit_id, origin, code)
    WHERE acknowledged_at IS NULL;
