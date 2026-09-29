-- CMD_SEQ applicato, dal poll: quale comando la CU ha davvero in opera.
--
-- Finora il server sapeva solo quale comando aveva *inviato* (control_unit.cmd_seq) e doveva
-- dedurre il resto dal CFG_VER. Ma il CFG_VER ha due autori — la CU lo incrementa, il server
-- lo prevede — e due contatori con due autori restano in passo solo finche' ogni comando
-- arriva ed entra in opera una volta sola. Alla prima divergenza il server non aveva modo di
-- sapere se la CU fosse avanti o indietro, ne' quale comando mancasse.
--
-- CMD_SEQ ha un autore solo: lo scrive il server nel prologo di blocco e la CU lo restituisce
-- nel poll. Confrontare i due valori non e' una previsione, e' una lettura.

ALTER TABLE public.control_unit
    ADD COLUMN applied_cmd_seq integer;

COMMENT ON COLUMN public.control_unit.applied_cmd_seq IS
    'CMD_SEQ dell''ultimo comando di configurazione che la CU dichiara di aver applicato (poll 0x0B); NULL se il firmware non lo trasmette ancora';
