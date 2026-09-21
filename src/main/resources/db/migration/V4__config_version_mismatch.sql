-- Disallineamento di configurazione fra server e CU.
--
-- I report con un CFG_VER diverso da quello atteso restano scartati: senza sapere quale
-- configurazione era attiva, quei byte non sono decodificabili nemmeno in seguito. Quello
-- che cambia e' che non spariscono piu' in silenzio: il contatore sotto rende visibile
-- quante misure si stanno perdendo e da quando.

ALTER TABLE public.control_unit
    ADD COLUMN config_mismatch_count bigint NOT NULL DEFAULT 0,
    ADD COLUMN last_config_mismatch_at timestamptz,
    -- L'ultimo CFG_VER che la CU ha dichiarato: confrontato con config_version dice di
    -- quanto e' indietro (o avanti) rispetto al server.
    ADD COLUMN last_reported_config_version integer;
