package com.polito.tesi.measuremanager.schedulers

import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class AirtimeResetService(private val cur: ControlUnitRepository) {

    @Scheduled(cron = "0 0 0 * * *")
    fun resetDailyAirtime() {
        val rowsAffected = cur.resetDailyAirtime()
        println("Reset giornaliero Airtime completato. Unità aggiornate: $rowsAffected")
    }
}
