// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.services.timestream;

import com.amazonaws.services.timestreamwrite.model.MeasureValueType;

import java.util.HashMap;
import java.util.Map;

public class TimestreamPoint {
    private String measureName;
    private MeasureValueType measureValueType;
    private String measureValue;
    private long time;
    private String timeUnit;
    private Map<String, String> dimensions;

    public TimestreamPoint() {
        this.dimensions = new HashMap<>();
    }

    public TimestreamPoint(TimestreamPoint anotherPoint,
                           String measureName, String measureValue, MeasureValueType measureValueType) {
        this.time = anotherPoint.time;
        this.timeUnit = anotherPoint.timeUnit;
        this.dimensions = new HashMap<>(anotherPoint.dimensions);
        this.measureName = measureName;
        this.measureValueType = measureValueType;
        this.measureValue = measureValue;
    }

    public TimestreamPoint(TimestreamPoint anotherPoint) {
        this(anotherPoint, anotherPoint.measureName, anotherPoint.measureValue, anotherPoint.measureValueType);
    }

    public String getMeasureName() {
        return measureName;
    }

    public void setMeasureName(String measureValue) {
        this.measureName = measureValue;
    }

    public String getMeasureValue() {
        return measureValue;
    }

    public void setMeasureValue(String measureValue) {
        this.measureValue = measureValue;
    }

    public MeasureValueType getMeasureValueType() {
        return measureValueType;
    }

    public void setMeasureValueType(MeasureValueType measureValueType) {
        this.measureValueType = measureValueType;
    }

    public void setMeasureValueType(String measureValueType) {
        this.measureValueType = MeasureValueType.fromValue(measureValueType.toUpperCase());
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Map<String, String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, String> dims) {
        this.dimensions = new HashMap<>(dims);
    }

    public void addDimension(String dimensionName, String dimensionValue) {
        dimensions.put(dimensionName, dimensionValue);
    }

    @Override
    public String toString() {
        return "TimestreamPoint{" +
                "measureName='" + measureName + '\'' +
                ", measureValueType=" + measureValueType +
                ", measureValue='" + measureValue + '\'' +
                ", time=" + time +
                ", timeUnit='" + timeUnit + '\'' +
                ", dimensions=" + dimensions +
                '}';
    }
}
