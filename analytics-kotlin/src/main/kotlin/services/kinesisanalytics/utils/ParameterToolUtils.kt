// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package services.kinesisanalytics.utils

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime
import org.apache.flink.api.java.utils.ParameterTool
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set


object ParameterToolUtils {

    private fun fromApplicationProperties(properties: Properties): ParameterTool {
        val map: MutableMap<String?, String?> = HashMap(properties.size)
        properties.forEach { k: Any?, v: Any? -> map[k as String?] = v as String? }
        return ParameterTool.fromMap(map)
    }

    fun fromArgsAndApplicationProperties(args: Array<String>?): ParameterTool {
        //read parameters from command line arguments (for debugging)
        var parameter = ParameterTool.fromArgs(args)

        //read the parameters from the Kinesis Analytics environment
        val applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties()
        val flinkProperties = applicationProperties["FlinkApplicationProperties"]
        if (flinkProperties != null) {
            parameter = parameter.mergeWith(fromApplicationProperties(flinkProperties))
        }
        return parameter
    }
}