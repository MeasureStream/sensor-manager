-- Una MU ha una storia anche quando non e' attaccata a niente.
--
-- Finora lo stacco cancellava il legame: `control_unit_id` tornava nullo e di dove fosse
-- montata non restava traccia. Una MU in un cassetto spariva dal server — lei, il suo stato,
-- i suoi errori e le sue misure, che pure il database conserva tutte.
--
-- Il legame attivo resta dov'e'. Lo percorrono la decodifica dei report, il pianificatore
-- della configurazione e le istantanee: aggiungerci un filtro ovunque sarebbe il modo di
-- sbagliarne uno. Accanto si aggiunge la memoria, che nessuno di quei percorsi legge.
--
-- `archived_at` e' la dissociazione chiesta dall'interfaccia: la MU esce dall'elenco di chi
-- la possiede senza che niente venga cancellato. Se ricompare in una notifica di topologia
-- torna visibile, perche' e' di nuovo in uso e nasconderla sarebbe una bugia.

ALTER TABLE public.measurement_unit
    ADD COLUMN last_control_unit_id bigint,
    ADD COLUMN detached_at  timestamptz,
    ADD COLUMN last_seen    timestamptz,
    ADD COLUMN archived_at  timestamptz;

ALTER TABLE public.measurement_unit
    ADD CONSTRAINT measurement_unit_last_control_unit_fk
        FOREIGN KEY (last_control_unit_id) REFERENCES public.control_unit(id) ON DELETE SET NULL;

-- Chi e' attaccato adesso ha gia' la sua risposta: si parte da li' invece che dal vuoto.
UPDATE public.measurement_unit
   SET last_control_unit_id = control_unit_id
 WHERE control_unit_id IS NOT NULL;

CREATE INDEX measurement_unit_last_control_unit_idx
    ON public.measurement_unit (last_control_unit_id);

COMMENT ON COLUMN public.measurement_unit.last_control_unit_id IS
    'Ultima CU su cui la MU e'' stata montata, anche dopo lo stacco';
COMMENT ON COLUMN public.measurement_unit.detached_at IS
    'Quando e'' sparita dalla topologia della sua CU; null se e'' ancora montata';
COMMENT ON COLUMN public.measurement_unit.last_seen IS
    'Ultima volta che la MU si e'' fatta viva: topologia, stato 0x12 o report';
COMMENT ON COLUMN public.measurement_unit.archived_at IS
    'Dissociata: fuori dall''elenco del proprietario, con tutti i dati conservati';
