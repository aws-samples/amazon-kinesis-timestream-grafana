# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
#
# Licensed under the MIT-0 License. See the LICENSE accompanying this file
# for the specific language governing permissions and limitations under
# the License.

from aws_cdk import (
    core,
    aws_ec2 as ec2,
    aws_ecs as ecs,
    aws_ecs_patterns as ecs_patterns,
    aws_efs as efs,
    aws_iam as iam,
    aws_logs as logs,
    aws_secretsmanager as secretsmanager,
    aws_timestream as timestream,
)
from aws_cdk.aws_ecs import PortMapping, MountPoint, EfsVolumeConfiguration, AuthorizationConfig
from aws_cdk.aws_efs import PosixUser, Acl


class GrafanaStack(core.Stack):

    def __init__(self, scope: core.Construct, construct_id: str,
                 database: timestream.CfnDatabase, table: timestream.CfnTable,
                 **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        vpc = ec2.Vpc(self, "GrafanaVpc", max_azs=2)

        cluster = ecs.Cluster(self, "MyCluster", vpc=vpc)

        file_system = efs.FileSystem(
            self, "EfsFileSystem",
            vpc=vpc,
            encrypted=True,
            lifecycle_policy=efs.LifecyclePolicy.AFTER_14_DAYS,
            performance_mode=efs.PerformanceMode.GENERAL_PURPOSE,
            throughput_mode=efs.ThroughputMode.BURSTING
        )

        access_point = efs.AccessPoint(
            self, "EfsAccessPoint",
            file_system=file_system,
            path="/var/lib/grafana",
            posix_user=PosixUser(
                gid="1000",
                uid="1000"
            ),
            create_acl=Acl(
                owner_gid="1000",
                owner_uid="1000",
                permissions="755"
            )
        )

        log_group = logs.LogGroup(self, "taskLogGroup",
                                  retention=logs.RetentionDays.ONE_MONTH
                                  )

        container_log_driver = ecs.LogDrivers.aws_logs(stream_prefix="fargate-grafana", log_group=log_group)

        task_role = iam.Role(self, "taskRole", assumed_by=iam.ServicePrincipal("ecs-tasks.amazonaws.com"))

        task_role.add_to_policy(iam.PolicyStatement(
            effect=iam.Effect.ALLOW,
            actions=[
                "cloudwatch:DescribeAlarmsForMetric",
                "cloudwatch:DescribeAlarmHistory",
                "cloudwatch:DescribeAlarms",
                "cloudwatch:ListMetrics",
                "cloudwatch:GetMetricStatistics",
                "cloudwatch:GetMetricData",
                "ec2:DescribeTags",
                "ec2:DescribeInstances",
                "ec2:DescribeRegions",
                "tag:GetResources"
            ],
            resources=["*"]
        ))
        self.grant_timestream_read(task_role, database, table)

        execution_role = iam.Role(self, "executionRole", assumed_by=iam.ServicePrincipal("ecs-tasks.amazonaws.com"))
        log_group.grant_write(execution_role)

        volume_name = "efsGrafanaVolume"

        volume_config = ecs.Volume(
            name=volume_name,
            efs_volume_configuration=EfsVolumeConfiguration(
                file_system_id=file_system.file_system_id,
                transit_encryption="ENABLED",
                authorization_config=AuthorizationConfig(access_point_id=access_point.access_point_id)
            ))

        task_definition = ecs.FargateTaskDefinition(
            self, "TaskDef",
            task_role=task_role,
            execution_role=execution_role,
            volumes=[volume_config]
        )

        grafana_admin_password = secretsmanager.Secret(self, "grafanaAdminPassword")
        grafana_admin_password.grant_read(task_role)

        container_web = task_definition.add_container(
            "grafana",
            image=ecs.ContainerImage.from_registry("grafana/grafana"),
            logging=container_log_driver,
            environment={
                "GF_INSTALL_PLUGINS": "grafana-timestream-datasource 1.1.0",
                "GF_AWS_default_REGION": core.Aws.REGION
            },
            secrets={
                "GF_SECURITY_ADMIN_PASSWORD": ecs.Secret.from_secrets_manager(
                    grafana_admin_password)
            })

        container_web.add_port_mappings(PortMapping(container_port=3000))
        container_web.add_mount_points(
            MountPoint(container_path="/var/lib/grafana", read_only=False, source_volume=volume_config.name)
        )

        fargate_service = ecs_patterns.ApplicationLoadBalancedFargateService(
            self, "MyFargateService",
            cluster=cluster,
            cpu=1024,
            desired_count=1,
            task_definition=task_definition,
            memory_limit_mib=2048,
            platform_version=ecs.FargatePlatformVersion.VERSION1_4
        )

        fargate_service.target_group.configure_health_check(path="/api/health")
        file_system.connections.allow_default_port_from(fargate_service.service.connections)

        core.CfnOutput(self, "GrafanaAdminSecret", value=grafana_admin_password,
                       export_name="GrafanaAdminSecret")

    def grant_timestream_read(self, execution_role, database, table):
        execution_role.add_to_policy(iam.PolicyStatement(
            effect=iam.Effect.ALLOW,
            actions=[
                "timestream:DescribeEndpoints",
                "timestream:ListDatabases",
                "timestream:SelectValues"
            ],
            resources=["*"]
        ))
        execution_role.add_to_policy(iam.PolicyStatement(
            effect=iam.Effect.ALLOW,
            actions=[
                "timestream:ListTables",
                "timestream:DescribeDatabase"
            ],
            resources=[database.attr_arn]
        ))
        execution_role.add_to_policy(iam.PolicyStatement(
            effect=iam.Effect.ALLOW,
            actions=[
                "timestream:Select",
                "timestream:ListMeasures",
                "timestream:DescribeTable"
            ],
            resources=[table.attr_arn]
        ))
