// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package services.kinesisanalytics

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants
import services.kinesisanalytics.operators.JsonToTimestreamPayloadFn
import services.kinesisanalytics.utils.ParameterToolUtils
import services.timestream.TimestreamInitializer
import services.timestream.TimestreamSink
import java.util.*


/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the [Flink Website](http://flink.apache.org/docs/stable/).
 *
 * <p>To package your application into a JAR file for execution, run
 * './gradlew shadowJar' on the command line.
 *
 *
 * If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the build.gradle.kts file (simply search for 'javaMainClass').
 */
object StreamingJob {
    private const val DEFAULT_STREAM_NAME = "timeseries-input-stream"
    private const val DEFAULT_REGION_NAME = "eu-west-1"
    private const val DEFAULT_DB_NAME = "timestreamDB"
    private const val DEFAULT_TABLE_NAME = "timestreamTable"

    private fun createKinesisSource(env: StreamExecutionEnvironment, parameter: ParameterTool): DataStream<String> {

        //set Kinesis consumer properties
        val kinesisConsumerConfig = Properties()
        //set the region the Kinesis stream is located in
        kinesisConsumerConfig.setProperty(
            AWSConfigConstants.AWS_REGION,
            parameter["Region", DEFAULT_REGION_NAME]
        )
        //obtain credentials through the DefaultCredentialsProviderChain, which includes the instance metadata
        kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO")
        val adaptiveReadSettingStr = parameter["SHARD_USE_ADAPTIVE_READS", "false"]
        if (adaptiveReadSettingStr == "true") {
            kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_USE_ADAPTIVE_READS, "true")
        } else {
            //poll new events from the Kinesis stream once every second
            kinesisConsumerConfig.setProperty(
                ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS,
                parameter["SHARD_GETRECORDS_INTERVAL_MILLIS", "1000"]
            )
            // max records to get in shot
            kinesisConsumerConfig.setProperty(
                ConsumerConfigConstants.SHARD_GETRECORDS_MAX,
                parameter["SHARD_GETRECORDS_MAX", "10000"]
            )
        }

        val stream = parameter["InputStreamName", DEFAULT_STREAM_NAME]
        //create Kinesis source
        return env.addSource(
            FlinkKinesisConsumer( //read events from the Kinesis stream passed in as a parameter
                stream,  //deserialize events with EventSchema
                SimpleStringSchema(),  //using the previously defined properties
                kinesisConsumerConfig
            )
        ).name("KinesisSource<${stream}>")
    }

    private fun createDatabaseAndTableIfNotExist(
        region: String,
        databaseName: String,
        tableName: String
    ) {
        val timestreamInitializer = TimestreamInitializer(region)
        timestreamInitializer.createDatabase(databaseName)
        timestreamInitializer.createTable(databaseName, tableName)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val parameter: ParameterTool = ParameterToolUtils.fromArgsAndApplicationProperties(args)

        // set up the streaming execution environment
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val region = parameter["Region", DEFAULT_REGION_NAME]
        val databaseName = parameter["TimestreamDbName", DEFAULT_DB_NAME]
        val tableName = parameter["TimestreamTableName", DEFAULT_TABLE_NAME]
        val batchSize = parameter["TimestreamIngestBatchSize", "75"].toInt()

        createDatabaseAndTableIfNotExist(region, databaseName, tableName)

        createKinesisSource(env, parameter)
            .map(JsonToTimestreamPayloadFn()).name("MaptoTimestreamPayload")
            .addSink(TimestreamSink(region, databaseName, tableName, batchSize))
            .name("TimestreamSink<$databaseName, $tableName>")

        // execute program
        env.execute("Flink Streaming Java API Skeleton")
    }
}