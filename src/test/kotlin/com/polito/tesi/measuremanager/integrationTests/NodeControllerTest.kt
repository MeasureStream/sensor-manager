package com.polito.tesi.measuremanager.integrationTests




import com.google.gson.*
import com.polito.tesi.measuremanager.dtos.ControlUnitDTO
import com.polito.tesi.measuremanager.dtos.MeasureDTO
import com.polito.tesi.measuremanager.dtos.MeasurementUnitDTO
import com.polito.tesi.measuremanager.dtos.NodeDTO
import org.json.JSONArray
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.geo.Point
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import java.time.Instant
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import kotlin.random.Random


/*class InstantTypeAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {
    override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src?.toString()) // Serializza come stringa ISO-8601
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Instant {
        return try {
            Instant.parse(json?.asString)
        } catch (e: DateTimeParseException) {
            throw JsonParseException("Invalid Instant format: ${json?.asString}", e)
        }
    }
}*/



@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NodeControllerTest @Autowired constructor(val mockMvc: MockMvc){
    companion object{
        @JvmStatic
        var setup = false
    }

    val baseUrl = "/API/"

    val controllerunitsUrl = "/API/controlunits"
    val measurementunitsUrl = "/API/measurementunits"
    val nodeUrl = "/API/nodes"






    val controlUnits = List(10) {
        ControlUnitDTO(
            id = it.toLong(),
            networkId = it.toLong() + 101,
            name = "ControlUnit-${it+1}",
            remainingBattery = Random.nextDouble(0.0, 100.0),
            rssi = Random.nextDouble(-100.0, 0.0),
            nodeId = (it%10).toLong() + 1

        )
    }

    val measurementUnits = List(20) {
        MeasurementUnitDTO(
            id = it.toLong(),
            networkId = it.toLong() + 201,
            type = listOf("Temperature", "Pressure", "Humidity").random(),
            measuresUnit = listOf("°C", "Pa", "%").random(),
            nodeId = (it%10).toLong() + 1
        )
    }

    val nodes = List(10) {
        NodeDTO(
            id = it.toLong(),
            name = "Node-${it+1}",
            standard = Random.nextBoolean(),
            controlUnitsId = setOf( ),
            measurementUnitsId = setOf( ),
            //controlUnitsId = setOf( it.toLong() ),
            //measurementUnitsId = setOf( it.toLong() , it.toLong() + 10 ) ,
            location = Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0)),
        )
    }


    fun upload_CU(cu: ControlUnitDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .post(controllerunitsUrl)
                .content(Gson().toJson(cu))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }
    fun update_CU(cu: ControlUnitDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .put(controllerunitsUrl)
                .content(Gson().toJson(cu))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }
    fun upload_MU(mu: MeasurementUnitDTO ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .post(measurementunitsUrl)
                .content(Gson().toJson(mu))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(status().isCreated())
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }

    fun update_MU(mu: MeasurementUnitDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .put(measurementunitsUrl)
                .content(Gson().toJson(mu))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }

    fun upload_Node(n : NodeDTO ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .post(nodeUrl)
                .content(Gson().toJson(n))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(status().isCreated())
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }

    fun update_Node(n: NodeDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .put(nodeUrl)
                .content(Gson().toJson(n))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }

    fun delete_Node(n: NodeDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .delete(nodeUrl)
                .content(Gson().toJson(n))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }


    @Test
    @BeforeAll
    fun init(): Unit {
        if(!setup) {
            nodes.forEach { upload_Node(it) }
            measurementUnits.forEach { upload_MU(it) }
            controlUnits.forEach { upload_CU(it, status().isCreated) }
            setup = true;

            println(measurementUnits)
            println(controlUnits)
            println(nodes)
        }
    }

    @Test
    fun `Create a node and verify it is correctly saved`() {
        upload_Node(NodeDTO(11,"node-11",false, setOf(), setOf(),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))))
    }

    @Test
    fun `Verify that a node without a name is not saved`() {
        update_Node(NodeDTO(10,"   ",false, setOf(), setOf(),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))),
            status().isConflict
        )
    }

    @Test
    fun `Add a ControlUnit Already Assigned to a Node `() {
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  2) , status().isAccepted )
        update_Node(NodeDTO(9,"node-11",false, controlUnitsId = setOf(8,1), measurementUnitsId = setOf(8),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))),
            status().isConflict
        )
    }
    @Test
    fun `Add a ControlUnit not Already Assigned to a Node `() {
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  null) , status().isAccepted )
        println(nodes[9])
        println(controlUnits[8])
        println(controlUnits[1])
        println(measurementUnits[8])

        mockMvc.get("${nodeUrl}/?id=9").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        //mockMvc.get("${controllerunitsUrl}/?id=1").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();

        update_Node(NodeDTO(9,"node-11",false, controlUnitsId = setOf(9,1), measurementUnitsId = setOf(9,19),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))),
            status().isAccepted
        )
    }

    @Test
    fun `Add a MeasurementUnit Already Assigned to a Node`() {
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 2), status().isAccepted )
        update_Node(NodeDTO(9,"node-10",false, controlUnitsId = setOf(8), measurementUnitsId = setOf(8,1),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))),
            status().isConflict
        )
    }
    @Test
    fun `Add a MeasurementUnit not Already Assigned to a Node`() {
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", null), status().isAccepted )
        mockMvc.get("${nodeUrl}/?id=5").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        update_Node(NodeDTO(5,"node-10",false, controlUnitsId = setOf(5), measurementUnitsId = setOf( 5, 15, 1),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))),
            status().isAccepted
        )
    }

    @Test
    fun `Delete a node and verify that its related entities are also removed`() {
        delete_Node(NodeDTO(7,"node-1",false, controlUnitsId = setOf(), measurementUnitsId = setOf(),Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0))),
            status().isAccepted)
    }





}

