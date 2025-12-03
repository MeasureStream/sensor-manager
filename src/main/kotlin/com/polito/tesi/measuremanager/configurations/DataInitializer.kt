package com.polito.tesi.measuremanager.configurations


import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Node
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import com.polito.tesi.measuremanager.repositories.UserRepository
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.geo.Point
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DataInitializer(
    val cur: ControlUnitRepository,
    val mur: MeasurementUnitRepository,
    val nr: NodeRepository,
    val ur: UserRepository
) : InitializingBean{
    @PostConstruct
    fun init(){
        print("initialize fake data")
    }

    override fun afterPropertiesSet() {

        val user = User().apply {
            name="polito"
            surname = "polito"
            email="polito@polito.it"
            userId = "b0be4ea5-17d3-4e63-ad81-510b4532dac8"//"6533c601-0db1-47a6-a150-f402cb142362"//"1d445807-c24e-4513-884d-22451ce9cf67"
            role = "customer"
            nodes= mutableSetOf()
            mus= mutableSetOf()
            cus= mutableSetOf()
        }
        ur.save(user)


        val user1 = User().apply {
            name="sacla"
            surname = "sacla"
            email="sacla@sacla.it"
            userId = "a8631830-c29b-40d6-811e-98dbcf488169"//"6533c601-0db1-47a6-a150-f402cb142362"//"1d445807-c24e-4513-884d-22451ce9cf67"
            role = "customer"
            nodes= mutableSetOf()
            mus= mutableSetOf()
            cus= mutableSetOf()
        }
        ur.save(user1)

        val c1 = ControlUnit().apply {
                networkId = (47).toLong()
                name = "ControlUnit-${47}"
                remainingBattery = Random.nextDouble(1.0, 100.0)
                rssi = Random.nextDouble(-100.0, 0.0)
                node = null
                this.user = user1
            }

        val m1 = MeasurementUnit().apply {
                        networkId =  47L
                        type = "Temperature"
                        measuresUnit = "°C"
                        node = null
                        this.user = user1
                    }

                    val n1 =  Node().apply{
                name = "Node-47"
                standard = false
                location = Point(Random.nextDouble(45.06,45.08), Random.nextDouble(7.5,7.6))//Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))
                //ownerId = "1d445807-c24e-4513-884d-22451ce9cf67"
                this.user = user1
            }
            c1.node = n1
            n1.controlUnits = mutableSetOf()
            n1.controlUnits.add(c1)

            m1.node = n1
            n1.measurementUnits = mutableSetOf()
            n1.measurementUnits.add(m1)

            user1.nodes.add(n1)
            user1.mus.add(m1)
            user1.cus.add(c1)

            nr.save(n1)
            cur.save(c1)
            mur.save(m1)





        val controlUnits = List(10) { i ->
            ControlUnit().apply {
                networkId = (i + 1).toLong()
                name = "ControlUnit-${i + 1}"
                remainingBattery = Random.nextDouble(1.0, 100.0)
                rssi = Random.nextDouble(-100.0, 0.0)
                node = null
                this.user = user
            }
        }


        val measurementUnits = List(20) { i ->
            when(i){
                0 -> {
                    MeasurementUnit().apply {
                        networkId =  1L
                        type = "Temperature"
                        measuresUnit = "°C"
                        node = null
                        this.user = user
                    }

                }
                10 -> {
                    MeasurementUnit().apply {
                        networkId =  11L
                        type = "Pressure"
                        measuresUnit = "Pa"
                        node = null
                        this.user = user
                    }
                }
                else -> {
                    MeasurementUnit().apply {
                        networkId = (i + 1).toLong()
                        type = listOf("Temperature", "Pressure", "Humidity").random()
                        measuresUnit = when (type) {
                            "Temperature" -> "°C"
                            "Pressure" -> "Pa"
                            "Humidity" -> "%"
                            else -> "unknown"
                        }

                        node = null
                        this.user = user
                    }
                }
            }

        }

        val nodes = MutableList(10) {
            Node().apply{
                name = "Node-${it+1}"
                standard = false
                location = Point(Random.nextDouble(45.06,45.08), Random.nextDouble(7.5,7.6))//Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))
                //ownerId = "1d445807-c24e-4513-884d-22451ce9cf67"
                this.user = user
            }
        }
        controlUnits.forEachIndexed { i, it ->
            it.node = nodes[i]
        }
        nodes.forEachIndexed { index, node ->
            node.controlUnits = mutableSetOf()
            node.controlUnits.add(controlUnits[index])
        }
        measurementUnits.forEachIndexed { i: Int, it: MeasurementUnit ->
            it.node = nodes[i % 10]
        }
        nodes.forEachIndexed { index: Int, node: Node ->
            node.measurementUnits = mutableSetOf()


            node.measurementUnits.add(measurementUnits[index])
            node.measurementUnits.add(measurementUnits[index+10])
        }

        user.nodes.addAll(nodes)
        user.mus.addAll(measurementUnits)
        user.cus.addAll(controlUnits)

        nr.saveAll(nodes)
        cur.saveAll(controlUnits)
        mur.saveAll(measurementUnits)


        // casi particolari

        val controlUnit = ControlUnit().apply {
                networkId = 50L
                name = "Control Unit DET Lab"
                remainingBattery = 100.0
                rssi = 0.0
                node = null
                this.user = user
            }
        val mesurementUnit = MeasurementUnit().apply {
            networkId =  21
            type = "Temperature"
            measuresUnit = "°C"
            node = null
            this.user = user
        }

        val node = Node().apply{
            name = "Lab_Misure"
            standard = false
            location = Point(45.06300697998612, 7.658806443214417)//Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))
            this.user = user
        }
        controlUnit.node = node
        node.controlUnits = mutableSetOf()
        node.controlUnits.add(controlUnit)

        mesurementUnit.node=node
        node.measurementUnits=mutableSetOf()
        node.measurementUnits.add(mesurementUnit)

        user.nodes.add(node)
        user.mus.add(mesurementUnit)
        user.cus.add(controlUnit)


        nr.save(node)
        cur.save(controlUnit)
        mur.save(mesurementUnit)

    }
}
