-- Quante volte lo stesso fatto si e' ripetuto, finche' l'avviso resta aperto.
--
-- Gli eventi della 0xA2 non hanno un rientro: la CU li rimanda a ogni messaggio finche' la
-- condizione dura. Una riga per uplink riempiva il taccuino di copie e la testata della
-- scheda di badge identici — sei «comunicazione col sensore fallita» uno accanto all'altro,
-- che non si leggono, si ignorano.
--
-- Adesso l'avviso aperto e' uno solo per (CU, MU, canale, codice) e questo numero dice
-- quante volte e' tornato. Serve: un guasto visto quaranta volte non e' lo stesso fatto di
-- uno visto una volta, e senza il contatore la differenza si perdeva nel deduplicare.

ALTER TABLE public.device_event
    ADD COLUMN occurrences integer NOT NULL DEFAULT 1;

COMMENT ON COLUMN public.device_event.occurrences IS
    'Quante volte il fatto si e'' ripetuto mentre l''avviso era aperto';
