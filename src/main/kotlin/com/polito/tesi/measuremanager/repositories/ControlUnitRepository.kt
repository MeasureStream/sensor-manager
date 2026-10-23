package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.ControlUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface ControlUnitRepository : JpaRepository<ControlUnit, Long>, PagingAndSortingRepository<ControlUnit, Long> {


    fun findAllByNameAndUser_UserId(
        name: String,
        ownerId: String,
    ): List<ControlUnit>

    fun findAllByUser_UserId(ownerId: String): List<ControlUnit>

    fun findByIdAndUser_UserId(
        id: Long,
        ownerId: String,
    ): ControlUnit?



    fun findAllByNameAndUser_UserId(
        name: String,
        ownerId: String,
        pageable: Pageable,
    ): Page<ControlUnit>

    fun findAllByUser_UserId(
        ownerId: String,
        pageable: Pageable,
    ): Page<ControlUnit>


    fun findAllByName(name: String): List<ControlUnit>

    fun findByDevEui(networkId: Long): ControlUnit?




    fun findAllByName(
        name: String,
        pageable: Pageable,
    ): Page<ControlUnit>

    @Modifying
    @Transactional
    @Query("UPDATE ControlUnit c SET c.usedDailyAirtime = 0")
    fun resetDailyAirtime(): Int

    /**
     * Incrementa il CMD_SEQ con **una sola istruzione**, dentro al database.
     *
     * Il giro leggi-incrementa-salva in memoria non bastava: due richieste in volo
     * contemporaneamente leggevano lo stesso numero e lo usavano entrambe. E un CMD_SEQ
     * ripetuto non e' un dettaglio estetico, perche' il protocollo dice che la CU deve
     * **ignorare** un comando con un numero gia' visto, riconoscendolo come ritrasmissione:
     * di due comandi diversi ne entrava in opera uno solo, mentre il server ne contava due e
     * si aspettava due incrementi di CFG_VER. Il 23 ottobre 2026 e' successo davvero, con i
     * numeri 08, 09 e 10 usciti due volte ciascuno.
     *
     * L'istruzione prende il lucchetto sulla riga e lo tiene fino alla fine della
     * transazione: chi arriva dopo aspetta, poi legge un numero nuovo. Per questo
     * l'allocazione va fatta **per prima** in un metodo transazionale — da li' in avanti
     * anche CFG_VER e il resto della riga sono al riparo.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "UPDATE control_unit SET cmd_seq = (cmd_seq + 1) % 256 WHERE id = :id",
        nativeQuery = true,
    )
    fun bumpCmdSeq(@Param("id") id: Long): Int

    /**
     * Rilegge il CMD_SEQ appena assegnato. Va chiamata nella stessa transazione di
     * [bumpCmdSeq]: e' il lucchetto preso li' a garantire che nessun altro possa infilarsi
     * fra l'incremento e la lettura.
     */
    @Query(value = "SELECT cmd_seq FROM control_unit WHERE id = :id", nativeQuery = true)
    fun readCmdSeq(@Param("id") id: Long): Int

}
