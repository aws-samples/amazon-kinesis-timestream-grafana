// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.services.kinesisanalytics.operators;

import com.amazonaws.services.timestream.TimestreamPoint;
import com.amazonaws.services.timestreamwrite.model.MeasureValueType;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonToTimestreamPayloadFn extends RichMapFunction<String, Collection<TimestreamPoint>> {

    private static final Logger LOG = LoggerFactory.getLogger(JsonToTimestreamPayloadFn.class);

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
    }

    @Override
    public Collection<TimestreamPoint> map(String jsonString) {
        HashMap<String, String> map = new Gson().fromJson(jsonString,
                new TypeToken<HashMap<String, String>>() {
                }.getType());
        TimestreamPoint basePoint = new TimestreamPoint();
        LOG.info("will map entity {}", map);
        Map<String, String> measures = new HashMap<>(map.size());

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // assuming these fields are present in every JSON record
            if (key.toLowerCase().endsWith("_measure")) {
                measures.put(key, value);
                continue;
            }

            switch (key.toLowerCase()) {
                case "time":
                    basePoint.setTime(Long.parseLong(value));
                    break;
                case "timeunit":
                    basePoint.setTimeUnit(value);
                    break;
                default:
                    basePoint.addDimension(key, value);
            }
        }

        LOG.info("mapped to point {}", basePoint);
        return measures.entrySet().stream()
                .map(measure -> new TimestreamPoint(
                        basePoint, measure.getKey(), measure.getValue(),
                        MeasureValueType.DOUBLE))
                .collect(Collectors.toList());
    }
}
