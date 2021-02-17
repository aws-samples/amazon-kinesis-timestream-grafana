# !/usr/bin/env python3

# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

from stacks.amazon_timestream_stack import AmazonTimeStreamStack
from aws_cdk import core
from stacks.grafana.grafana_stack import GrafanaStack
from stacks.kinesis.amazon_kinesis_analytics_source_stack import KinesisAnalyticsSource
from stacks.kinesis.amazon_kinesis_analytics_stack import KinesisAnalyticsStack
from stacks.kinesis.amazon_kinesis_stream_stack import KinesisStreamStack
from pathlib import Path
from stacks.sample_kinesis_stream_producer.sample_kinesis_stream_producer_stack import SampleKinesisStreamProducerStack

app = core.App()

kda_path = app.node.try_get_context("kda_path")

if kda_path is None:
    kda_path = "../analytics/target/analytics-timestream-java-sample-1.0.jar"
    print("No context defined variable kda_path for Amazon Kinesis Data Analytics for Apache Flink "
          "application jar file path defined. Will use default path <" + kda_path + ">.")

if not Path(kda_path).is_file():
    print("Warning, Apache Flink application jar file not found: <"
          + kda_path + ">. Make sure file exists or you've built default application, "
                       "check analytics/README.md or analytics-kotlin/README.md for more information")

timestream_stack = AmazonTimeStreamStack(app, "amazon-timestream")

kinesis_stream = KinesisStreamStack(app, 'amazon-kinesis-stream-stack')

kinesis_analytics_source_stack = KinesisAnalyticsSource(app, "flink-source-bucket",
                                                        stream=kinesis_stream.stream,
                                                        kda_path=kda_path,
                                                        database=timestream_stack.database,
                                                        table=timestream_stack.table)

stream_producer_stack = SampleKinesisStreamProducerStack(app, "sample-kinesis-stream-producer",
                                                         stream=kinesis_stream.stream)

kinesis_analytics_stack = KinesisAnalyticsStack(app, "amazon-kinesis-analytics", stream=kinesis_stream.stream,
                                                db_name=timestream_stack.database.database_name,
                                                table_name=timestream_stack.table.table_name,
                                                kda_role=kinesis_analytics_source_stack.kda_role,
                                                log_group_name=kinesis_analytics_source_stack.log_group_name,
                                                log_stream_name=kinesis_analytics_source_stack.log_stream_name,
                                                asset=kinesis_analytics_source_stack.asset)
kinesis_analytics_stack.add_dependency(kinesis_analytics_source_stack)

grafana_stack = GrafanaStack(app, "grafana",
                             database=timestream_stack.database, table=timestream_stack.table)

app.synth()
