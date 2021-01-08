# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
#
# Licensed under the MIT-0 License. See the LICENSE accompanying this file
# for the specific language governing permissions and limitations under
# the License.

from aws_cdk import (
    core,
    aws_iam as iam,
    aws_kinesisanalytics as kda,
    aws_kinesis as kinesis,
    aws_s3_assets as assets
)


class KinesisAnalyticsStack(core.Stack):

    def __init__(self, scope: core.Construct, construct_id: str, stream: kinesis.IStream, db_name: str, table_name: str,
                 kda_role: iam.IRole, log_group_name: str, log_stream_name: str, asset: assets.Asset, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        batch_size_param = core.CfnParameter(self, "batchSizeParam", type="Number",
                                             min_value=1, max_value=100, default=75,
                                             description="Number of recoreds ingested from stream before flushing to "
                                                         "Timestream database")

        kda_application = kda.CfnApplicationV2(self, "KdaApplication",
                                               runtime_environment="FLINK-1_11",
                                               service_execution_role=kda_role.role_arn,
                                               application_name=core.Aws.STACK_NAME,
                                               application_configuration={
                                                   "environmentProperties": {
                                                       "propertyGroups": [
                                                           {
                                                               "propertyGroupId": "FlinkApplicationProperties",
                                                               "propertyMap": {
                                                                   "InputStreamName": stream.stream_name,
                                                                   "Region": core.Aws.REGION,
                                                                   "TimestreamDbName": db_name,
                                                                   "TimestreamTableName": table_name,
                                                                   "TimestreamIngestBatchSize": batch_size_param.value_as_number
                                                               },
                                                           },
                                                       ]
                                                   },
                                                   "flinkApplicationConfiguration": {
                                                       "monitoringConfiguration": {
                                                           "logLevel": "INFO",
                                                           "metricsLevel": "TASK",
                                                           "configurationType": "CUSTOM"
                                                       },
                                                       "parallelismConfiguration": {
                                                           "autoScalingEnabled": False,
                                                           "parallelism": 1,
                                                           "parallelismPerKpu": 1,
                                                           "configurationType": "CUSTOM"
                                                       },
                                                       "checkpointConfiguration": {
                                                           "configurationType": "CUSTOM",
                                                           "checkpointInterval": 60_000,
                                                           "minPauseBetweenCheckpoints": 60_000,
                                                           "checkpointingEnabled": True
                                                       }
                                                   },
                                                   "applicationSnapshotConfiguration": {
                                                       "snapshotsEnabled": False
                                                   },
                                                   "applicationCodeConfiguration": {
                                                       "codeContent": {
                                                           "s3ContentLocation": {
                                                               "bucketArn": asset.bucket.bucket_arn,
                                                               "fileKey": asset.s3_object_key
                                                           }
                                                       },
                                                       "codeContentType": "ZIPFILE"
                                                   },
                                               }
                                               )

        kda_logging = kda.CfnApplicationCloudWatchLoggingOptionV2(
            self, "FlinkLogging",
            application_name=kda_application.application_name,
            cloud_watch_logging_option=kda.CfnApplicationCloudWatchLoggingOptionV2.CloudWatchLoggingOptionProperty(
                log_stream_arn="arn:{}:logs:{}:{}:log-group:{}:log-stream:{}".format(
                    core.Aws.PARTITION, core.Aws.REGION, core.Aws.ACCOUNT_ID,
                    log_group_name, log_stream_name)))

        kda_logging.add_depends_on(kda_application)

        core.CfnOutput(self, "KdaApplicationName", value=kda_application.application_name,
                       export_name="KdaApplicationName")
