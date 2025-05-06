package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.DCCDTO
import com.polito.tesi.measuremanager.services.DCCService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@RestController
@RequestMapping("/API/dcc")
class DCCController(private val dcs: DCCService) {
    @GetMapping("/", "")
    fun get() : List<DCCDTO>{
        return dcs.getAllDccs()
    }

    //@PostMapping("/","")
    @PostMapping("/{muId}")
    fun upload(@RequestParam("file")  file: MultipartFile, @PathVariable muId: Long, @RequestParam expiration: LocalDate ): DCCDTO{
        val dcc = DCCDTO(1, expiration, muId, file.originalFilename!! )
        return dcs.create(dcc,file)
    }
    @GetMapping("/{id}/download")
    fun downloadPdf(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val fileBytes: ByteArray = dcs.getpdf(id)!!
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.setContentDisposition(
            ContentDisposition.builder("attachment")
            .filename("certificato_$id.pdf")
            .build())

        return ResponseEntity.ok()
            .headers(headers)
            .body(fileBytes)
    }
    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long){
        dcs.delete(id)
    }

}