package com.polito.tesi.measuremanager.template

data class SensorTemplate(
        val modelName: String,
        val type: String,
        val unit: String? = null,
        val ranges: Map<String, Map<String, Any>>? = null,
        val conversion: Map<String, Any>? = null,
        val properties: Map<String, Any>? = null,
        val calibration: Map<String, Any>? = null,
        val metrology: Map<String, Any>? = null,
)
