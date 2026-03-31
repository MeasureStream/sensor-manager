package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.*
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.hmac.NetworkIdEncoder
import com.polito.tesi.measuremanager.kafka.KafkaCuProducer
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
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
        if (ss.isAdmin())
            {

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
        return cur.findByIdAndUser_UserId(id, userId)?.toDTO(templateService) ?: throw EntityNotFoundException("ControlUnit $id not found")
    }

    override fun getAllControlUnitsPage(
        page: Pageable,
        name: String?,
    ): Page<ControlUnitDTO> {
        if (ss.isAdmin())
            {

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
       TODO("ancora da implementare")


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

        val userId = ss.getCurrentUserId()
        val cu = cur.findById(id).get()
        val cuCreateDTO = cu.toCuCreateDTO()

        cur.deleteById(id)
        val event = EventCU(eventType = "DELETE", cu = cuCreateDTO)
        kcu.sendCuCreate(event)
    }


    @Transactional
    override fun onJoinNotification(c: CuJoinNotification) {
        // 1. Recupera o crea la CU
        val cu = getOrCreateControlUnit(c.devEui)

        // 2. Sincronizza le MU (Measurement Units)
        c.muList.forEach { muDesc ->
            val mu = mur.findByExtendedId(muDesc.extendedId) ?: MeasurementUnit().apply {
                extendedId = muDesc.extendedId
            }

            mu.controlUnit = cu
            mu.localId = muDesc.localId
            mu.model = muDesc.model

            mur.save(mu)
        }

        cur.save(cu)
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



    /**
     * Funzione di supporto per garantire l'idempotenza:
     * Se la CU esiste la restituisce, altrimenti ne crea una "orfana" pronta per il claim.
     */
    private fun getOrCreateControlUnit(devEui: Long): ControlUnit {
        return cur.findByDevEui(devEui) ?: ControlUnit().apply {
            this.devEui = devEui
            // Generiamo il networkId (l'hash per il QR/Claim) partendo dal devEui

            this.name = "New Device (${devEui})"
            this.user = null // Rimane null finché l'utente non fa il claim
            this.remainingBattery = 100.0
        }
    }



}
