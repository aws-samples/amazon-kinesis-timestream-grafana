// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package services.kinesisanalytics.operators

import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import services.timestream.TimestreamPoint
import java.util.concurrent.TimeUnit

class OffsetFutureTimestreamPoints : ProcessFunction<Collection<TimestreamPoint>, Collection<TimestreamPoint>>() {
    companion object {
        private val TIMESTREAM_FUTURE_THRESHOLD = TimeUnit.MINUTES.toMillis(15)
    }

    @Override
    override fun processElement(
        points: Collection<TimestreamPoint>, ctx: Context,
        out: Collector<Collection<TimestreamPoint>>
    ) {
        points.stream()
            .filter { pointTimestamp(it) > System.currentTimeMillis() + TIMESTREAM_FUTURE_THRESHOLD }
            .forEach {
                it.time = ctx.timestamp()
                it.timeUnit = TimeUnit.MILLISECONDS.name
            }
        out.collect(points)
    }

    private fun pointTimestamp(p: TimestreamPoint) =
        TimeUnit.valueOf(p.timeUnit
            .takeIf { it == "MILLISECONDS" || it == "SECONDS" || it == "MICROSECONDS" || it == "NANOSECONDS" }
            ?: TimeUnit.MILLISECONDS.name)
            .toMillis(p.time)
}
