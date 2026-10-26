-- Ripristino automatico della configurazione dopo un riavvio, per singola CU.
--
-- Il caso: un nodo riparte, perde la configurazione e dichiara CFG_VER 0 mentre il server ne
-- conserva una. Da quel momento ogni report viene scartato, perche' la mappa degli slot con
-- cui e' stato prodotto non e' quella che il server si aspetta, e nessuno interviene.
--
-- Il rimedio non e' automatico per tutti: rimandare una configurazione occupa l'etere e
-- riscrive il comportamento di un dispositivo senza che nessuno l'abbia chiesto in quel
-- momento. Si attiva per la singola CU, e la scelta si conserva qui perche' e' una proprieta'
-- del dispositivo, non della sessione di chi guarda la pagina.
--
-- Il valore predefinito e' falso: chi non sa che esiste non se lo ritrova acceso.

ALTER TABLE public.control_unit
    ADD COLUMN auto_restore_config boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.control_unit.auto_restore_config IS
    'Se dopo un riavvio con CFG_VER 0, e a topologia delle MU invariata, il server rimanda la configurazione';
