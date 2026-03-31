package com.polito.tesi.measuremanager.configurations


import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component


// TODO ("AGGIUSTA QUESTO COMPONENTE")
@Profile("dev")
@Component
class DataInitializer(

) : InitializingBean {
    @PostConstruct
    fun init()  {
        print("initialize fake data")
    }

    override fun afterPropertiesSet() {

        print("initialize fake data")
    }
}
