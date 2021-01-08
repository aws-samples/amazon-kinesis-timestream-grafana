<!-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved. SPDX-License-Identifier: MIT-0 -->

# Apache Flink sample data connector

Sample application that reads data from Amazon Kinesis Data Streams and writes to Amazon Timestream. This is similar to
the one in `../analytics` but uses `Kotlin` programming language and `Gradle` tooling.

----

## How to test it

Java 11 is the recommended version for using Amazon Kinesis Data Analytics for Apache Flink Application. If you have
multiple Java versions ensure to export Java 11 to your `JAVA_HOME` environment variable.

1. Optional for local testing: Create an Amazon Kinesis Data Stream with the name "TimestreamTestStream". You can use
   the below AWS CLI command:
   ```shell
   $ aws kinesis create-stream --stream-name TimestreamTestStream --shard-count 1
   ```

1. Optional for local testing: Compile and run the sample app locally.

   Upon start up the application checks if a Timestream database and tables exists and tries to create one if it can not
   find them.
   ```shell
   $ ./gradlew clean build
   $ ./gradlew run -Dexec.args="--InputStreamName TimestreamTestStream --TimestreamDbName TimestreamTestDatabase --TimestreamTableName TestTable"
   ``` 
   NOTE: You might need to change the version of timestreamwrite and timestreamquery dependencies in `build.gradle` file
   based on the version of SDK jar you are using.

   By default this sample app batches Timestream ingest records in batch of 75. This can be adjusted
   using `--TimestreamIngestBatchSize` option.
   ```shell
   $ ./gradlew  clean compile
   $ ./gradlew run -Dexec.args="--InputStreamName TimestreamTestStream --TimestreamDbName TimestreamTestDatabase --TimestreamTableName TestTable --TimestreamIngestBatchSize 75"
   ```    
1. Package application for deployment in Amazon Kinesis Data Analytics for Apache Flink

   ```shell
   $ ./gradlew clean shadowJar
   ```
   This will create a `jar` package in the directory `./build/libs/`. Use the fat jar package for deployment as it
   contains all needed dependencies.

## For sending data into the Amazon Kinesis Data Stream

You can deploy the lambda found in `../cdk/stacks/sample_kinesis_stream_producer/producer_lambda`. Or you can use the
instructions on
[sample script to generate a continuous stream of records that are ingested into Timestream](https://github.com/awslabs/amazon-timestream-tools/tree/master/tools/kinesis_ingestor)
as guideline.

## For deploying the sample application to Kinesis Data Analytics for Apache Flink

This sample application is part of the setup for transferring your time series data from Amazon Kinesis directly into
Amazon Timestream.

For the full set of instructions
[check information here](https://docs.aws.amazon.com/timestream/latest/developerguide/ApacheFlink.html)