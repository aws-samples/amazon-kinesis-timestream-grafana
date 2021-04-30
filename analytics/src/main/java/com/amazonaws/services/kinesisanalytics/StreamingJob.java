// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.services.kinesisanalytics;

import com.amazonaws.services.kinesisanalytics.operators.JsonToTimestreamPayloadFn;
import com.amazonaws.services.kinesisanalytics.operators.OffsetFutureTimestreamPoints;
import com.amazonaws.services.kinesisanalytics.utils.ParameterToolUtils;
import com.amazonaws.services.timestream.TimestreamInitializer;
import com.amazonaws.services.timestream.TimestreamSink;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;

import java.util.Properties;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

    private static final String DEFAULT_STREAM_NAME = "timeseries-input-stream";
    private static final String DEFAULT_REGION_NAME = "eu-west-1";
    private static final String DEFAULT_DB_NAME = "timestreamDB";
    private static final String DEFAULT_TABLE_NAME = "timestreamTable";

    public static DataStream<String> createKinesisSource(StreamExecutionEnvironment env, ParameterTool parameter) {

        //set Kinesis consumer properties
        Properties kinesisConsumerConfig = new Properties();
        //set the region the Kinesis stream is located in
        kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION,
                parameter.get("Region", DEFAULT_REGION_NAME));
        //obtain credentials through the DefaultCredentialsProviderChain, which includes the instance metadata
        kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");

        String adaptiveReadSettingStr = parameter.get("SHARD_USE_ADAPTIVE_READS", "false");

        if (adaptiveReadSettingStr.equals("true")) {
            kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_USE_ADAPTIVE_READS, "true");
        } else {
            //poll new events from the Kinesis stream once every second
            kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS,
                    parameter.get("SHARD_GETRECORDS_INTERVAL_MILLIS", "1000"));
            // max records to get in shot
            kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_MAX,
                    parameter.get("SHARD_GETRECORDS_MAX", "10000"));
        }

        //create Kinesis source

        return env.addSource(new FlinkKinesisConsumer<>(
                //read events from the Kinesis stream passed in as a parameter
                parameter.get("InputStreamName", DEFAULT_STREAM_NAME),
                //deserialize events with EventSchema
                new SimpleStringSchema(),
                //using the previously defined properties
                kinesisConsumerConfig
        )).name("KinesisSource");
    }

    public static void main(String[] args) throws Exception {
        final ParameterTool parameter = ParameterToolUtils.fromArgsAndApplicationProperties(args);

        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        final String region = parameter.get("Region", DEFAULT_REGION_NAME);
        final String databaseName = parameter.get("TimestreamDbName", DEFAULT_DB_NAME);
        final String tableName = parameter.get("TimestreamTableName", DEFAULT_TABLE_NAME);
        final int batchSize = Integer.parseInt(parameter.get("TimestreamIngestBatchSize", "75"));

        TimestreamInitializer timestreamInitializer = new TimestreamInitializer(region);
        timestreamInitializer.createDatabase(databaseName);
        timestreamInitializer.createTable(databaseName, tableName);

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setAutoWatermarkInterval(1000L);

        createKinesisSource(env, parameter)
                .map(new JsonToTimestreamPayloadFn()).name("MaptoTimestreamPayload")
                .process(new OffsetFutureTimestreamPoints()).name("UpdateFutureOffsetedTimestreamPoints")
                .addSink(new TimestreamSink(region, databaseName, tableName, batchSize))
                .name("TimeSeries<" + databaseName + ", " + tableName + ">");

        // execute program
        env.execute("Flink Streaming Java API Skeleton");
    }
}
