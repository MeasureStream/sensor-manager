package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.DeviceEvent
import com.polito.tesi.measuremanager.entities.EventOrigin
import com.polito.tesi.measuremanager.repositories.DeviceEventRepository
import com.polito.tesi.measuremanager.template.StatusBit
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Il bit di stato che il dizionario classifica come accaduto, non come condizione. */
private const val EVENT_KIND = "event"

/**
 * Il taccuino del server: i fatti che non arrivano sulla 0xA2.
 *
 * Prima di questa classe il server sapeva cose che non aveva dove scrivere. La topologia
 * delle MU cambiata compariva come badge finche' la CU teneva alzato il bit, e spariva quando
 * lo abbassava: nessuno poteva dire «visto», e nella scheda «Eventi e diagnostica» restava
 * scritto «nessun evento registrato» mentre in alto lampeggiava l'avviso. Lo stesso per un
 * comando che non risultava applicato: un confronto fra due numeri, vero o falso adesso, e
 * senza storia.
 *
 * Qui i due casi diventano righe nella stessa tabella degli eventi del dispositivo, con
 * l'origine a dire chi li ha visti. Da li' discendono le due cose che servivano: la **X**
 * sul badge, che e' la presa in carico della riga, e lo **storico**, che resta.
 *
 * La regola che tiene in piedi tutto: **un solo evento aperto per tipo**. Un bit di stato
 * resta alzato per ore mentre il poll passa ogni minuto; senza questo vincolo la stessa
 * topologia cambiata diventerebbe centinaia di righe e la scheda sarebbe illeggibile. Quando
 * l'utente prende in carico quella che c'e', il fatto puo' ripetersi e nascera' una riga
 * nuova — ed e' giusto, perche' e' successo di nuovo.
 */
@Component
class ServerEventRecorder(private val events: DeviceEventRepository) {

    private val log = LoggerFactory.getLogger(ServerEventRecorder::class.java)

    /**
     * Registra i bit appena comparsi nella parola di stato che il dizionario chiama eventi.
     *
     * Il codice e' il numero del bit: nello spazio `status` non si confonde con i codici
     * della 0xA2, e chi confronta con la documentazione trova il numero che si aspetta.
     */
    fun recordStatusEvents(cu: ControlUnit, appeared: List<StatusBit>) {
        appeared.filter { it.kind == EVENT_KIND }.forEach { flag ->
            record(
                    cu = cu,
                    origin = EventOrigin.STATUS,
                    code = flag.bit,
                    description = flag.description.ifBlank { flag.meaning },
            )
        }
    }

    /**
     * Scrive un evento se non ce n'e' gia' uno aperto dello stesso tipo.
     *
     * @return la riga creata, oppure null se ce n'era gia' una aperta.
     */
    fun record(cu: ControlUnit, origin: String, code: Int, description: String): DeviceEvent? {
        val already =
                events.findByControlUnit_IdAndOriginAndCodeAndAcknowledgedAtIsNull(cu.id, origin, code)
        if (already.isNotEmpty()) return null

        val event =
                events.save(
                        DeviceEvent(
                                controlUnit = cu,
                                receivedAt = OffsetDateTime.now(),
                                origin = origin,
                                // Un evento della CU: nessuna MU, nessun canale.
                                source = 0,
                                sensorIndex = 0xFF,
                                code = code,
                                description = description,
                                // L'istante e' quello in cui il server se n'e' accorto, e non
                                // c'e' un'eta' da cui ricavarne un altro: qui `occurredAt`
                                // coincide con `receivedAt` e non porta incertezza.
                                occurredAt = OffsetDateTime.now(),
                        )
                )
        log.info("CU {}: registrato evento {}/{} — {}", cu.deviceId, origin, code, description)
        return event
    }

    /**
     * Chiude gli eventi aperti di un tipo, perche' la condizione e' rientrata da sola.
     *
     * E' il caso del comando che risulta applicato dopo essere sembrato perso: lasciare
     * aperto un avviso che non vale piu' insegna all'utente a ignorare gli avvisi.
     */
    fun close(cu: ControlUnit, origin: String, code: Int): Int {
        val open = events.findByControlUnit_IdAndOriginAndCodeAndAcknowledgedAtIsNull(cu.id, origin, code)
        if (open.isEmpty()) return 0
        val now = OffsetDateTime.now()
        open.forEach { it.acknowledgedAt = now }
        events.saveAll(open)
        log.info("CU {}: chiusi {} eventi {}/{}, la condizione e' rientrata", cu.deviceId, open.size, origin, code)
        return open.size
    }
}
