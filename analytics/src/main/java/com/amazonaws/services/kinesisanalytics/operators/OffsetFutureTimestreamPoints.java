// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package com.amazonaws.services.kinesisanalytics.operators;

import com.amazonaws.services.timestream.TimestreamPoint;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class OffsetFutureTimestreamPoints
        extends ProcessFunction<Collection<TimestreamPoint>, Collection<TimestreamPoint>> {

    private static final long TIMESTREAM_FUTURE_THRESHOLD = TimeUnit.MINUTES.toMillis(15);

    @Override
    public void processElement(Collection<TimestreamPoint> timestreamPoints, Context context,
                               Collector<Collection<TimestreamPoint>> collector) {

        timestreamPoints.stream()
                .filter(p -> pointTimestamp(p) > System.currentTimeMillis() + TIMESTREAM_FUTURE_THRESHOLD)
                .forEach(p -> {
                    p.setTime(context.timestamp());
                    p.setTimeUnit(TimeUnit.MILLISECONDS.name());
                });
        collector.collect(timestreamPoints);
    }

    private long pointTimestamp(TimestreamPoint point) {
        String timeUnit = TimeUnit.MILLISECONDS.name();
        if ("MILLISECONDS".equals(point.getTimeUnit())
                || "SECONDS".equals(point.getTimeUnit())
                || "MICROSECONDS".equals(point.getTimeUnit())
                || "NANOSECONDS".equals(point.getTimeUnit())) {
            timeUnit = point.getTimeUnit();
        }
        return TimeUnit.valueOf(timeUnit).toMillis(point.getTime());
    }
}
