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
import java.time.format.DateTimeParseException
import kotlin.random.Random






@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ControlUnitControllerTest @Autowired constructor(val mockMvc: MockMvc){
    companion object{
        @JvmStatic
        var setup = false
    }

    val baseUrl = "/API/"
    val measuresUrl = "/API/measures"
    val controllerunitsUrl = "/API/controlunits"
    val measurementunitsUrl = "/API/measurementunits"
    val nodeUrl = "/API/nodes"

    val nodes = List(10) {
        NodeDTO(
            id = it.toLong(),
            name = "Node-${it+1}",
            standard = Random.nextBoolean(),
            controlUnitsId = setOf( ),
            measurementUnitsId = setOf( ),
            location = Point(Random.nextDouble(-180.0, 180.0), Random.nextDouble(-90.0, 90.0)),
        )
    }
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

    fun delete_CU(cu: ControlUnitDTO, httpStatusCode : ResultMatcher ): MvcResult {


        val response = mockMvc.perform(
            MockMvcRequestBuilders
                .delete(controllerunitsUrl)
                .content(Gson().toJson(cu))
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
            controlUnits.forEach { upload_CU(it, status().isCreated) }
            setup = true;
        }
    }



    @Test
    fun list_controlunits() {
        val response = mockMvc.get(controllerunitsUrl).andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        val json = JSONArray(response.response.contentAsString);

        assert(json.length() >= controlUnits.size)
    }

    @Test
    fun insert_control_unit_alreadyIdPresent(){
        upload_CU( ControlUnitDTO(1, 111, "ControlUnit-d16215c7-efb4-4742-a6f1-3f0722410415", 3.32, -74.19,  1) , status().isCreated )
    }
    @Test
    fun insert_control_unit_alreadyNIdPresent(){
        upload_CU( ControlUnitDTO(12, 110, "ControlUnit-d16215c7-efb4-4742-a6f1-3f0722410415", 3.32, -74.19,  1) , status().isConflict )
    }
    @Test
    fun insert_control_unit_RemainingBattery_out(){
        upload_CU( ControlUnitDTO(12, 112, "ControlUnit-d16215c7-efb4-4742-a6f1-3f0722410415", 103.32, -74.19, 1) , status().isConflict )
        upload_CU( ControlUnitDTO(13, 113, "ControlUnit-d16215c7-efb4-4742-a6f1-3f0722410415", -103.32, -74.19,  1) , status().isConflict )
    }
    @Test
    fun insert_control_unit_noName(){
        upload_CU( ControlUnitDTO(12, 112, "   ", 6.0, -74.19,  1) , status().isConflict )

    }
    @Test
    fun insert_control_unit_rssiPositive(){
        upload_CU( ControlUnitDTO(12, 112, "", 99.0, 5.0,1) , status().isConflict )

    }

    @Test
    fun update_control_unit_alreadyIdPresent(){
        update_CU( ControlUnitDTO(2, 102, "ControlUnit", 99.0, -10.0,  1) , status().isAccepted )
    }
    @Test
    fun update_control_unit_alreadyNIdNotPresent(){
        update_CU( ControlUnitDTO(1, 120, "ControlUnit not present", 3.32, -74.19,  1) , status().isNotFound )
    }
    @Test
    fun update_control_unit_RemainingBattery_out(){
        update_CU( ControlUnitDTO(1, 101, "ControlUnit-d16215c7-efb4-4742-a6f1-3f0722410415", 103.32, -74.19,  1) , status().isConflict )
        update_CU( ControlUnitDTO(1, 101, "ControlUnit-d16215c7-efb4-4742-a6f1-3f0722410415", -103.32, -74.19,  1) , status().isConflict )
    }
    @Test
    fun update_control_unit_noName(){
        update_CU( ControlUnitDTO(1, 101, "   ", 6.0, -74.19,  1) , status().isConflict )

    }
    @Test
    fun update_control_unit_rssiPositive(){
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, 5.0,  1) , status().isConflict )

    }

    @Test
    fun update_control_unit_change_Node(){
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  2) , status().isAccepted )
    }
    @Test
    fun update_control_unit_change_Node_NotNullToNull(){
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  2) , status().isAccepted )
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  null) , status().isAccepted )
    }
    @Test
    fun update_control_unit_change_Node_NullToNotNull(){
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  null) , status().isAccepted )
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  2) , status().isAccepted )

    }
    @Test
    fun update_control_unit_change_Node_NullToNull(){
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -5.0,  null) , status().isAccepted )
        update_CU( ControlUnitDTO(1, 101, "ciao", 99.0, -74.0,  null) , status().isAccepted )

    }

    @Test
    fun delete_control_unit (){
        val response = mockMvc.get("${nodeUrl}/?id=${9}").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        val json = JSONArray(response.response.contentAsString);



        val objectMapper = ObjectMapper().registerKotlinModule()
        val jsonNode: JsonNode = objectMapper.readTree(response.response.contentAsString)
        println(jsonNode)
        val list_measurementUnitsNode: JsonNode = jsonNode.get(0).get("controlUnitsId")
        val length_before = list_measurementUnitsNode.size()


        delete_CU(  ControlUnitDTO(9, 109, "ciao", 99.0, -5.0,  2) , status().isAccepted )

        val response1 = mockMvc.get("${nodeUrl}/?id=${9}").andExpect { jsonPath("$").isArray }.andDo { print() }.andReturn();
        val json1 = JSONArray(response.response.contentAsString);


        val jsonNode1: JsonNode = objectMapper.readTree(response1.response.contentAsString)
        val list_measurementUnitsNode1: JsonNode = jsonNode1.get(0).get("controlUnitsId")
        val length_after = list_measurementUnitsNode1.size()

        assert(length_after + 1  == length_before )

    }







}

