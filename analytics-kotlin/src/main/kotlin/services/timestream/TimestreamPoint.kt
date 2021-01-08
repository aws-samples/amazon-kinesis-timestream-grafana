// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package services.timestream

import com.amazonaws.services.timestreamwrite.model.MeasureValueType

data class TimestreamPoint(
    var measureName: String? = null,
    var measureValueType: MeasureValueType? = null,
    var measureValue: String? = null,
    var time: Long = 0,
    var timeUnit: String? = null,
    private var dimensions: MutableMap<String, String> = HashMap()
) {

    fun getDimensions(): Map<String, String> {
        return dimensions.toMap()
    }

    fun addDimension(dimensionName: String, dimensionValue: String) {
        dimensions[dimensionName] = dimensionValue
    }
}