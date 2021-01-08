# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
#
# Licensed under the MIT-0 License. See the LICENSE accompanying this file
# for the specific language governing permissions and limitations under
# the License.

from aws_cdk import (
    core,
    aws_cloudwatch as cloudwatch,
    aws_iam as iam,
    aws_kinesis as kinesis,
    aws_logs as logs,
    aws_s3_assets as assets,
    aws_timestream as timestream
)

from aws_cdk.aws_logs import RetentionDays
from aws_cdk.core import RemovalPolicy


class KinesisAnalyticsSource(core.Stack):

    def __init__(self, scope: core.Construct, construct_id: str,
                 stream: kinesis.IStream, kda_path: str,
                 database: timestream.CfnDatabase, table: timestream.CfnTable,
                 **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        asset = assets.Asset(self, "flink-source", path=kda_path)

        log_group = logs.LogGroup(self, "KdaLogGroup",
                                  retention=RetentionDays.FIVE_DAYS,
                                  removal_policy=RemovalPolicy.DESTROY)
        log_stream = log_group.add_stream("KdaLogStream")

        kda_role = iam.Role(self, "KdaRole",
                            assumed_by=iam.ServicePrincipal("kinesisanalytics.amazonaws.com"),
                            )

        asset.grant_read(kda_role)
        stream.grant_read(kda_role)
        cloudwatch.Metric.grant_put_metric_data(kda_role)
        log_group.grant(kda_role, "logs:DescribeLogStreams")
        log_group.grant_write(kda_role)

        kda_role.add_to_policy(iam.PolicyStatement(
            actions=["timestream:DescribeEndpoints",
                     "timestream:ListTables",
                     "timestream:ListDatabases",
                     "timestream:DescribeTable",
                     "timestream:DescribeDatabase",
                     ],
            resources=["*"]
        ))

        kda_role.add_to_policy(iam.PolicyStatement(
            actions=["timestream:*Database"],
            resources=[database.attr_arn]
        ))

        kda_role.add_to_policy(iam.PolicyStatement(
            actions=["timestream:*Table", "timestream:WriteRecords"],
            resources=[table.attr_arn]
        ))

        kda_role.add_to_policy(iam.PolicyStatement(
            actions=["kms:DescribeKey"],
            resources=["*"]
        ))

        kda_role.add_to_policy(iam.PolicyStatement(
            actions=["kms:CreateGrant"],
            resources=["*"],
            conditions={
                "ForAnyValue:StringEquals": {
                    "kms:EncryptionContextKeys": "aws:timestream:database-name"
                },
                "Bool": {
                    "kms:GrantIsForAWSResource": True
                },
                "StringLike": {
                    "kms:ViaService": "timestream.*.amazonaws.com"
                }
            }
        ))

        kda_role.add_to_policy(iam.PolicyStatement(actions=["kinesis:ListShards"], resources=[stream.stream_arn]))

        self._asset = asset
        self._kda_role = kda_role
        self._log_group_name = log_group.log_group_name
        self._log_stream_name = log_stream.log_stream_name

    @property
    def asset(self) -> assets.Asset:
        return self._asset

    @property
    def kda_role(self) -> iam.IRole:
        return self._kda_role

    @property
    def log_group_name(self) -> str:
        return self._log_group_name

    @property
    def log_stream_name(self) -> str:
        return self._log_stream_name
