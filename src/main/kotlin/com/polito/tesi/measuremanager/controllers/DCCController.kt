package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.DCCDTO
import com.polito.tesi.measuremanager.services.DCCService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/API/dcc")
class DCCController(private val dcs: DCCService) {
    @GetMapping("/", "")
    fun get() : List<DCCDTO>{
        return dcs.getAllDccs()
    }

    @PostMapping("/","")
    fun upload(@RequestBody dcc:DCCDTO, @RequestParam("file") file: MultipartFile): DCCDTO{
        return dcs.update(dcc,file)
    }
}