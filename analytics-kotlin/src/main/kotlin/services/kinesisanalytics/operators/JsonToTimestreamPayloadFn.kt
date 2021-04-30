// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package services.kinesisanalytics.operators

import com.amazonaws.services.timestreamwrite.model.MeasureValueType
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.apache.flink.api.common.functions.RichMapFunction
import org.slf4j.LoggerFactory
import services.timestream.TimestreamPoint
import java.util.*
import java.util.stream.Collectors

class JsonToTimestreamPayloadFn : RichMapFunction<String, Collection<TimestreamPoint>>() {

    companion object {
        private val LOG = LoggerFactory.getLogger(JsonToTimestreamPayloadFn::class.java)
    }

    @Override
    @Throws(Exception::class)
    override fun map(jsonString: String): List<TimestreamPoint> {
        val map = Gson().fromJson<HashMap<String, String>>(
            jsonString,
            object : TypeToken<HashMap<String, String>>() {}.type
        )
        val basePoint = TimestreamPoint()
        val measures = HashMap<String, String>(map.size)

        for ((key, value) in map) {
            if (key.toLowerCase().endsWith("_measure")) {
                measures[key] = value
                continue
            }

            when (key.toLowerCase()) {
                "time" -> basePoint.time = value.toLong()
                "timeunit" -> basePoint.timeUnit = value
                else -> basePoint.addDimension(key, value)
            }
        }
        LOG.trace("mapped to point {}", basePoint)

        return measures.entries.stream()
            .map {
                basePoint.copy(
                    measureName = it.key, measureValue = it.value,
                    measureValueType = MeasureValueType.DOUBLE
                )
            }
            .collect(Collectors.toList())
    }

}