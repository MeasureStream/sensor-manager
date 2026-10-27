-- La gravita' di un evento: non tutto cio' che il server registra e' un problema.
--
-- Finora ogni riga del taccuino nasceva come avviso, e l'interfaccia le mostrava tutte in
-- giallo. Ma «la configurazione e' tornata in passo» e «la configurazione non e' applicata»
-- sono due fatti opposti, e mostrarli dello stesso colore insegna a ignorarli entrambi.
--
-- Due valori soltanto: `info` per cio' che e' accaduto ed e' andato bene, `warning` per cio'
-- che richiede attenzione. Il colore lo sceglie l'interfaccia da qui, senza conoscere i
-- codici: un codice nuovo non obbliga a toccare il frontend.

ALTER TABLE public.device_event
    ADD COLUMN severity varchar(8) NOT NULL DEFAULT 'warning';

COMMENT ON COLUMN public.device_event.severity IS
    'info = fatto andato a buon fine, warning = richiede attenzione';
