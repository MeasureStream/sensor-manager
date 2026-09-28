-- Uplink del protocollo v1.2: poll 0x0B, notifica di topologia 0x11, stato MU 0x12.
--
-- Fino alla v1.1 il server sapeva di una CU solo cio' che gli serviva per disegnarla:
-- batteria, potenza, modello. La v1.2 aggiunge tre cose che finora non aveva modo di
-- conoscere, e che vanno conservate perche' descrivono lo stato del dispositivo fra un
-- contatto e l'altro:
--   * quale versione di protocollo parla la CU, che e' la condizione per dismettere le
--     vecchie FPort (nessuna si tocca finche' esiste una CU che non dichiara la 1.2);
--   * la parola di stato, sedici bit di cui quattro sono eventi latchati;
--   * la versione del modello dichiarata da ciascuna MU, che e' quella con cui vanno letti
--     i suoi slot: prima si prendeva il MAJOR piu' alto pubblicato e si sperava.

ALTER TABLE public.control_unit
    -- ProtoVer del poll 0x0B, su un byte: 0x12 = protocollo v1.2. NULL = mai dichiarato,
    -- cioe' firmware precedente alla v1.2.
    ADD COLUMN protocol_ver       integer,
    -- I due byte Status del poll, come sono arrivati. I testi si risolvono a ogni lettura
    -- con il dizionario di protocollo: se domani un bit riservato acquista significato,
    -- basta pubblicare il dizionario, senza rileggere questa colonna.
    ADD COLUMN status_word        integer,
    ADD COLUMN status_at          timestamptz,
    -- ALARM_SEQ dichiarato nel poll: confrontato con last_alarm_seq dice se un messaggio di
    -- allarme si e' perso per strada, anche quando il messaggio non e' mai arrivato.
    ADD COLUMN reported_alarm_seq integer,
    -- STATUS_SEQ dell'ultimo 0x12 ricevuto, per lo stesso controllo di continuita'.
    ADD COLUMN last_status_seq    integer;

ALTER TABLE public.measurement_unit
    -- MU Tmpl. Ver. della notifica 0x11: il MAJOR del modello con cui la MU e' stata
    -- programmata. NULL = notifica 0x10, che non lo porta.
    ADD COLUMN model_major integer,
    -- Parola di stato della MU dal comando 0x12 (bit 0-6 dalla MU, 7-9 dalla CU).
    ADD COLUMN status_word integer,
    ADD COLUMN status_at   timestamptz;

COMMENT ON COLUMN public.control_unit.protocol_ver IS
    'ProtoVer del poll 0x0B: 18 (0x12) per il protocollo v1.2, NULL per i firmware precedenti';
COMMENT ON COLUMN public.measurement_unit.model_major IS
    'MAJOR del modello di MU dichiarato nella notifica 0x11';
