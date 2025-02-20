package com.polito.tesi.measuremanager.integrationTests



import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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






@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MeasurementUnitTest @Autowired constructor(val mockMvc: MockMvc){
    companion object{
        @JvmStatic
        var setup = false
    }

    val baseUrl = "/API/"
    val measuresUrl = "/API/measures"
    val controllerunitsUrl = "/API/controlunits"
    val measurementunitsUrl = "/API/measurementunits"
    val nodeUrl = "/API/nodes"


    val measurementUnits = List(20) {
        MeasurementUnitDTO(
            id = it.toLong(),
            networkId = it.toLong() + 201,
            type = listOf("Temperature", "Pressure", "Humidity").random(),
            measuresUnit = listOf("°C", "Pa", "%").random(),
            idDcc = it.toLong() + 137,
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



    fun upload_MU(mu: MeasurementUnitDTO, httpStatusCode : ResultMatcher  ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .post(measurementunitsUrl)
                .content(Gson().toJson(mu))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
        //.andExpect(MockMvcResultMatchers.content().json(Gson().toJson(c)))

        return response.andReturn();
    }

    fun delete_MU(mu: MeasurementUnitDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .delete(measurementunitsUrl)
                .content(Gson().toJson(mu))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(MockMvcResultHandlers.print())
            .andExpect(httpStatusCode)
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


    @Test
    @BeforeAll
    fun init(): Unit {
        if(!setup) {
            nodes.forEach { upload_Node(it) }
            measurementUnits.forEach { upload_MU(it, status().isCreated) }


            setup = true;
        }
    }
/*
    @Test

    fun list_measures() {
        val response = mockMvc.get("${measuresUrl}/all").andExpect { jsonPath("$").isArray }.andDo{print()}.andReturn();
        val json = JSONArray(response.response.contentAsString);

        assert(json.length() == measures.size)
    }
*/

    @Test
    fun list_measuresunits() {
        val response = mockMvc.get(measurementunitsUrl).andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        val json = JSONArray(response.response.contentAsString);

        assert(json.length() >= measurementUnits.size)
    }
    @Test
    fun insert_measurement_unit_alreadyIdPresent(){
        upload_MU( MeasurementUnitDTO(1, 301, "Pressure", "Percentage", 830, 10), status().isCreated )
    }
    @Test
    fun insert_measurement_unit_alreadyNIdPresent(){
        upload_MU( MeasurementUnitDTO(201, 201, "Pressure", "Percentage", 830, 10), status().isConflict )
    }
    @Test
    fun insert_measurement_unit_idcc_negative(){
        upload_MU( MeasurementUnitDTO(475, 475, "Pressure", "Percentage", -830, 10), status().isConflict )
    }
    @Test
    fun insert_measurement_unit_BlankType(){
        upload_MU( MeasurementUnitDTO(500, 500, "   ", "Percentage", 830, 10), status().isConflict )

    }
    @Test
    fun insert_measurement_unit_BlankMeasuresUnit(){
        upload_MU( MeasurementUnitDTO(5011, 50011, "AAAA", " ", 830, 10), status().isConflict )

    }

    @Test
    fun update_measurement_unit_alreadyIdPresent(){
        update_MU(  MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, 10), status().isAccepted )
    }
    @Test
    fun update_measurement_unit_alreadyNIdNotPresent(){
        update_MU(  MeasurementUnitDTO(4, 500, "Temperature", "Percentage", 830, 10), status().isNotFound )
    }
    @Test
    fun update_measurement_unit_BlankType(){
        update_MU( MeasurementUnitDTO(1, 201, "   ", "Percentage", 830, 10), status().isConflict )
    }
    @Test
    fun update_measurement_unit_BlankMeasuresUnit(){
        update_MU( MeasurementUnitDTO(1, 201, "AAA", "", 830, 10), status().isConflict )

    }
    @Test
    fun update_measurement_unit_idDccNegative(){
        update_MU( MeasurementUnitDTO(1, 201, "AAA", "", -830, 10), status().isConflict )

    }

    @Test
    fun update_measurement_change_Node(){
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, 8), status().isAccepted )

    }
    @Test
    fun update_measurement_change_Node_NotNullToNull(){
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, 8), status().isAccepted )
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, null), status().isAccepted )
    }
    @Test
    fun update_measurement_change_Node_NullToNotNull(){
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, null), status().isAccepted )
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, 8), status().isAccepted )

    }

    @Test
    fun update_measurement_change_Node_NullToNull(){
        update_MU( MeasurementUnitDTO(1, 201, "Temperature", "Percentage", 831, null), status().isAccepted )
        update_MU( MeasurementUnitDTO(1, 201, "Pressure", "Percentage", 831, null), status().isAccepted )

    }


    @Test
    fun delete_measurement (){
        val response = mockMvc.get("${nodeUrl}/?id=${9}").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        val json = JSONArray(response.response.contentAsString);



        val objectMapper = ObjectMapper().registerKotlinModule()
        val jsonNode: JsonNode = objectMapper.readTree(response.response.contentAsString)
        println(jsonNode)
        val list_measurementUnitsNode: JsonNode = jsonNode.get(0).get("measurementUnitsId")
        val length_before = list_measurementUnitsNode.size()


        delete_MU( MeasurementUnitDTO(19, 219, "Temperature", "Percentage", 837, 9), status().isAccepted )

        val response1 = mockMvc.get("${nodeUrl}/?id=${9}").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        val json1 = JSONArray(response.response.contentAsString);


        val jsonNode1: JsonNode = objectMapper.readTree(response1.response.contentAsString)
        val list_measurementUnitsNode1: JsonNode = jsonNode1.get(0).get("measurementUnitsId")
        val length_after = list_measurementUnitsNode1.size()

        assert(length_after + 1  == length_before )

    }





}

