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
                                             description="Number of records ingested from stream before flushing to "
                                                         "Timestream database")
        kda_application = kda.CfnApplicationV2(
            self, "KdaApplication",
            runtime_environment="FLINK-1_11",
            service_execution_role=kda_role.role_arn,
            application_name=core.Aws.STACK_NAME,
            application_configuration=
            kda.CfnApplicationV2.ApplicationConfigurationProperty(
                application_code_configuration=kda.CfnApplicationV2.ApplicationCodeConfigurationProperty(
                    code_content=kda.CfnApplicationV2.CodeContentProperty(
                        s3_content_location=kda.CfnApplicationV2.S3ContentLocationProperty(
                            bucket_arn=asset.bucket.bucket_arn,
                            file_key=asset.s3_object_key
                        )
                    ),
                    code_content_type="ZIPFILE"
                ),
                environment_properties=kda.CfnApplicationV2.EnvironmentPropertiesProperty(
                    property_groups=[
                        kda.CfnApplicationV2.PropertyGroupProperty(
                            property_group_id="FlinkApplicationProperties",
                            property_map={
                                "InputStreamName": stream.stream_name,
                                "Region": core.Aws.REGION,
                                "TimestreamDbName": db_name,
                                "TimestreamTableName": table_name,
                                "TimestreamIngestBatchSize": batch_size_param.value_as_number
                            }
                        )
                    ]
                ),
                application_snapshot_configuration=kda.CfnApplicationV2.ApplicationSnapshotConfigurationProperty(
                    snapshots_enabled=False
                ),
                flink_application_configuration=kda.CfnApplicationV2.FlinkApplicationConfigurationProperty(
                    monitoring_configuration=kda.CfnApplicationV2.MonitoringConfigurationProperty(
                        configuration_type="CUSTOM",
                        log_level="INFO",
                        metrics_level="TASK"
                    ),
                    parallelism_configuration=kda.CfnApplicationV2.ParallelismConfigurationProperty(
                        configuration_type="CUSTOM",
                        auto_scaling_enabled=False,
                        parallelism=1,
                        parallelism_per_kpu=1
                    ),
                    checkpoint_configuration=kda.CfnApplicationV2.CheckpointConfigurationProperty(
                        configuration_type="CUSTOM",
                        # the properties below are optional
                        checkpointing_enabled=True,
                        checkpoint_interval=60_000,
                        min_pause_between_checkpoints=60_000
                    )
                )
            )
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
