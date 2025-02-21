package com.polito.tesi.measuremanager.configurations


import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Node
import com.polito.tesi.measuremanager.repositories.ControlUnitRepository
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.NodeRepository
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.geo.Point
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DataInitializer(
    val cur: ControlUnitRepository,
    val mur: MeasurementUnitRepository,
    val nr: NodeRepository
) : InitializingBean{
    @PostConstruct
    fun init(){
        print("initialize fake data")
    }

    override fun afterPropertiesSet() {

        val controlUnits = List(10) { i ->
            ControlUnit().apply {
                networkId = (i + 1).toLong()
                name = "ControlUnit-${i + 1}"
                remainingBattery = Random.nextDouble(1.0, 100.0)
                rssi = Random.nextDouble(-100.0, 0.0)
                node = null
            }
        }

        val measurementUnits = List(20) { i ->
            MeasurementUnit().apply {
                networkId = (i + 1).toLong()
                type = listOf("Temperature", "Pressure", "Humidity").random()
                measuresUnit = listOf("°C", "Pa", "%").random()
                idDcc = (i + 137 + 1).toLong()
                node = null

            }
        }

        val nodes = MutableList(10) {
            Node().apply{
                name = "Node-${it+1}"
                standard = false
                location = Point(Random.nextDouble(45.06,45.08), Random.nextDouble(7.5,7.6))//Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))
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

        nr.saveAll(nodes)
        cur.saveAll(controlUnits)
        mur.saveAll(measurementUnits)
    }
}