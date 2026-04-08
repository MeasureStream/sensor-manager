package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.*
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.hmac.NetworkIdEncoder
import com.polito.tesi.measuremanager.kafka.KafkaCuProducer
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service


@Service
class ControlUnitServiceImpl(
    private val cur: ControlUnitRepository,
    private val mur: MeasurementUnitRepository,
    private val ss: SecurityService,
    private val kcu: KafkaCuProducer,
    private val templateService: TemplateService,
) : ControlUnitService {

    override fun getAllControlUnits(
        name: String?,
    ): List<ControlUnitDTO> {
        if (ss.isAdmin()) {

            name?.let {
                return cur.findAllByName(it).map { e -> e.toDTO(templateService) }
            }
            return cur.findAll().map { it.toDTO(templateService) }
        }

        val userId = ss.getCurrentUserId()

        name?.let {
            return cur.findAllByNameAndUser_UserId(it, userId).map { e -> e.toDTO(templateService) }
        }
        return cur.findAllByUser_UserId(userId).map { it.toDTO(templateService) }
    }

    override fun getControlUnit(id: Long): ControlUnitDTO? {
        if (ss.isAdmin()) {
            return cur.findByIdOrNull(id)?.toDTO(templateService)
        }
        val userId = ss.getCurrentUserId()
        return cur.findByIdAndUser_UserId(id, userId)?.toDTO(templateService)
            ?: throw EntityNotFoundException("ControlUnit $id not found")
    }

    override fun getAllControlUnitsPage(
        page: Pageable,
        name: String?,
    ): Page<ControlUnitDTO> {
        if (ss.isAdmin()) {

            return cur.findAll(page).map { it.toDTO(templateService) }
        }
        val userId = ss.getCurrentUserId()

        return cur.findAllByUser_UserId(userId, page).map { it.toDTO(templateService) }
    }


    @Transactional
    override fun update(
        id: Long,
        c: ControlUnitDTO,
    ): ControlUnitDTO {
        TODO("QUI DOVRANNO essere Mandati i comandi")


    }


    @Transactional
    override fun claimControlUnit(hash: String): ControlUnitDTO {
        // 1. Decodifica il networkId dall'hash (es. QR code sul dispositivo)
        val decodedDevEui = NetworkIdEncoder.decode(hash)
        val currentUser = ss.getOrCreateCurrentUser()

        val cu = cur.findByDevEui(decodedDevEui)
            ?: throw EntityNotFoundException("Il dispositivo non è ancora stato censito dalla rete. Accendilo e riprova.")

        // 3. Controllo sicurezza: è già di qualcuno?
        if (cu.user != null) {
            throw OperationNotAllowed("Questa Control Unit è già stata rivendicata da un altro utente.")
        }

        // 4. Associazione (Claim)
        cu.user = currentUser
        cu.name = "La mia CU $decodedDevEui" // L'utente potrà rinominarla dopo

        return cur.save(cu).toDTO(templateService)
    }

    override fun delete(id: Long) {
        if (!ss.isAdmin()) throw OperationNotAllowed("You can't delete a ControlUnit if you are not an admin")

        val cu = cur.findById(id).get()
        val cuCreateDTO = cu.toCuCreateDTO()

        cur.deleteById(id)
        val event = EventCU(eventType = "DELETE", cu = cuCreateDTO)
        kcu.sendCuCreate(event)
    }


    fun createMuByModel(
        extendedId: Long,
        model: Int,
        localId: Int
    ): MeasurementUnit {
        val mu =
            MeasurementUnit().apply {
                this.extendedId = extendedId
                this.model = model
                this.sensors = mutableListOf()
                this.localId = localId
            }

        // Funzione helper interna per aggiungere sensori alla MU
        fun addSensor(
            modelName: String,
            index: Int,
        ) {
            val sensor =
                Sensor(
                    modelName = modelName,
                    measurementUnit = mu,
                    sensorIndex = index,
                )
            mu.sensors.add(sensor)
        }

        when (model) {
            1 -> {
                addSensor("accelerometer_lsm6dsm", 1)
                addSensor("pressure_ms5837", 2)
                addSensor("humidity_hpp845e", 3)
                addSensor("ntc_temperature", 4)
            }

            100 -> {
                addSensor("accelerometer_lsm6dsm", 1)
                addSensor("ntc_temperature", 2)
            }

            else -> throw OperationNotAllowed("Model $model not supported")
        }

        return mu
    }


    @Transactional
    override fun onJoinNotification(c: CuJoinNotification) {
        // 1. Recupera o crea la CU
        val cu = getOrCreateControlUnit(c.devEui)
        val savedCu = cur.save(cu)

        // 2. PULIZIA: Scolleghiamo le MU esistenti
        // Usiamo .toList() per creare una copia della lista ed evitare problemi di iterazione
        // mentre modifichiamo i riferimenti
        savedCu.measurementUnits.toList().forEach { mu ->
            mu.controlUnit = null
            mur.save(mu)
        }
        // Svuotiamo la lista lato CU per sincronizzare lo stato in memoria
        savedCu.measurementUnits.clear()

        // 3. Elaborazione della nuova lista hardware
        c.muList.forEach { muDesc ->
            var mu = mur.findByExtendedId(muDesc.extendedId)

            if (mu == null) {
                mu = createMuByModel(muDesc.extendedId, muDesc.model, muDesc.localId)
            } else {
                mu.model = muDesc.model
                mu.localId = muDesc.localId
            }

            // 4. SINCRONIZZAZIONE
            mu.controlUnit = savedCu
            mu.user = savedCu.user

            // Importante: aggiungiamo la MU alla lista della CU per mantenere la coerenza bidirezionale
            savedCu.measurementUnits.add(mu)

            mur.save(mu)
        }

        // Il save finale della CU aggiornerà tutto l'albero
        cur.save(savedCu)
    }

        @Transactional
        override fun onStatusUpdate(c: CuStatusUpdate) {
            // 1. Recupera o crea la CU
            val cu = getOrCreateControlUnit(c.devEui)

            // 2. Aggiorna i parametri dinamici (Byte 5 e 6-7 della tabella)
            // Convertiamo il batteryLevel (0-255 o 0-100) nel Double dell'entità
            cu.remainingBattery = c.batteryLevel.toDouble()

            // Se hai un campo per lo stato grezzo o per il modello della CU
            // cu.statusRaw = c.statusRaw
            cu.model = c.model

            cur.save(cu)
        }

    @Transactional
    override fun onSignalUpdate(dto: SignalQualityUpdate) {
        // 1. Conversione DevEUI da String (Hex) a Long
        val devEuiLong = try {
            dto.devEUI.toLong(16)
        } catch (e: Exception) {
            println("Errore conversione DevEUI: ${dto.devEUI}")
            return
        }

        // 2. Recupero della Control Unit
        val cu = cur.findByDevEui(devEuiLong) ?: run {
            println("Segnale ricevuto per CU non censita: $devEuiLong")
            return
        }

        // 3. Mappatura dei campi in base alla tua Entity
        cu.rssi = dto.rssi.toDouble()

        // Mappatura del DataRate:
        // Se dto.dataRate è "DR5", estraiamo solo il numero 5
        cu.dataRate = try {
            dto.dataRate.replace("DR", "").toInt()
        } catch (e: Exception) {
            0 // Valore di fallback se il formato non è DRx
        }

        cu.lastSeen = LocalDateTime.parse(dto.time, java.time.format.DateTimeFormatter.ISO_DATE_TIME)

        // Aggiorniamo SF e BW (che avevamo messo nel DTO o che possiamo estrarre)
        // Nota: Se hai aggiornato il DTO SignalQualityUpdate includendo SF e BW:
         cu.spreadingFactor = dto.spreadingFactor
         cu.bandwidth = (dto.bandwidth / 1000).toInt() // Salviamo in kHz se preferisci

        // 4. Salvataggio
        cur.save(cu)

        println("Aggiornato segnale per CU ${cu.name} [EUI: ${dto.devEUI}]: RSSI=${cu.rssi}, DR=${cu.dataRate}")
    }


    /**
         * Funzione di supporto per garantire l'idempotenza:
         * Se la CU esiste la restituisce, altrimenti ne crea una "orfana" pronta per il claim.
         */
        fun getOrCreateControlUnit(devEui: Long): ControlUnit {
            return cur.findByDevEui(devEui) ?: ControlUnit().apply {
                this.devEui = devEui
                // Generiamo il networkId (l'hash per il QR/Claim) partendo dal devEui

                this.name = "New Device (${devEui})"
                this.user = null // Rimane null finché l'utente non fa il claim
                this.remainingBattery = 100.0
            }
        }


    }

