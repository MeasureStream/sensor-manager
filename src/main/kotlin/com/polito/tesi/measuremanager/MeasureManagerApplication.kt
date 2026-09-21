package com.polito.tesi.measuremanager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MeasureManagerApplication

fun main(args: Array<String>) {
    runApplication<MeasureManagerApplication>(*args)
}
