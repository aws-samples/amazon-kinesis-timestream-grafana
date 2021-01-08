# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

from aws_cdk import (
    aws_events_targets,
    aws_kinesis,
    aws_lambda,
    aws_lambda_python,
    core
)
from aws_cdk.aws_events import Rule, Schedule, RuleTargetInput
from aws_cdk.core import Duration


class SampleKinesisStreamProducerStack(core.Stack):

    def __init__(self, scope: core.Construct, construct_id: str, stream: aws_kinesis.IStream, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        sample_device_producer = aws_lambda_python.PythonFunction(self, 'SampleDeviceProducer',
                                                                  entry='stacks/sample_kinesis_stream_producer/producer_lambda',
                                                                  index='app.py',
                                                                  runtime=aws_lambda.Runtime.PYTHON_3_8,
                                                                  timeout=core.Duration.seconds(30))

        stream.grant_write(sample_device_producer)

        lambda_input = {"Stream": stream.stream_name}
        Rule(self, 'ProducerTriggerEventRule',
             enabled=True,
             schedule=Schedule.rate(Duration.minutes(1)),
             targets=[aws_events_targets.LambdaFunction(handler=sample_device_producer,
                                                        event=RuleTargetInput.from_object(lambda_input))])
