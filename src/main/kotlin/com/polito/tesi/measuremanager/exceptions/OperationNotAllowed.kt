package com.polito.tesi.measuremanager.exceptions

/**
 * Un rifiuto motivato: il server ha capito la richiesta e dice di no. Diventa un 409.
 *
 * Estende [RuntimeException] e non [Exception] per due motivi che non sono stilistici.
 *
 * **Il rollback.** `@Transactional` annulla la transazione solo per le eccezioni non
 * controllate: con una controllata Spring **conferma** quello che il metodo aveva gia'
 * scritto e poi rilancia. Un rifiuto a meta' metodo lascerebbe quindi nel database la parte
 * di lavoro fatta prima del no — ed e' esattamente il modo in cui si perde un valore che
 * l'utente crede di aver impostato.
 *
 * **I proxy.** Fra il controllore e il servizio ci sono i proxy dell'AOP, e le interfacce
 * Kotlin non dichiarano nessun `throws`. Una eccezione controllata che attraversa un proxy
 * dinamico viene incartata in `UndeclaredThrowableException`: a quel punto il gestore che
 * la mappa sul 409 non la riconosce piu' e il client riceve un 500 con il corpo generico,
 * senza che nel log compaia niente di utile.
 */
class OperationNotAllowed(message: String) : RuntimeException(message)
