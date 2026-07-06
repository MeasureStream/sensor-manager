package com.polito.tesi.measuremanager.dtos

/**
 * Limiti hardware/protocollo dei dispositivi.
 * Una CU può montare al massimo [MAX_MUS_PER_CU] MU, ognuna con al massimo
 * [MAX_SENSORS_PER_MU] sensori. Il totale configurabile per CU è però
 * [MAX_SENSORS_PER_CU]: i sensori identificati oltre questa soglia
 * (in ordine di localId MU e sensorIndex) restano visibili ma NON configurabili.
 */
const val MAX_MUS_PER_CU = 8
const val MAX_SENSORS_PER_MU = 12
const val MAX_SENSORS_PER_CU = 48
