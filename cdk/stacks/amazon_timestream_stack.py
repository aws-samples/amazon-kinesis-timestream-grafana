# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

from aws_cdk import (
    aws_timestream as timestream,
    core
)


class AmazonTimeStreamStack(core.Stack):

    def __init__(self, scope: core.Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        memory_retention_param = core.CfnParameter(self, "memoryRetentionParam", type="Number",
                                                   min_value=1, max_value=8766, default=6,
                                                   description="The duration (in hours) for which data must be retained "
                                                               "in the memory store per table.")

        magnetic_retention_param = core.CfnParameter(self, "magneticRetentionParam", type="Number",
                                                     min_value=1, max_value=73000, default=15,
                                                     description="The duration (in days) for which data must be retained "
                                                                 "in the magnetic store per table.")

        database = timestream.CfnDatabase(self, id="TimestreamDatabase", database_name="TimestreamDB")

        retention = {
            "MemoryStoreRetentionPeriodInHours": memory_retention_param.value_as_number,
            "MagneticStoreRetentionPeriodInDays": magnetic_retention_param.value_as_number
        }

        table = timestream.CfnTable(self, "SampleMetricsTable", database_name=database.database_name,
                                    retention_properties=retention,
                                    table_name="SampleMetricsTable")
        table.add_depends_on(database)

        self._database = database
        self._table = table

    @property
    def database(self) -> timestream.CfnDatabase:
        return self._database

    @property
    def table(self) -> timestream.CfnTable:
        return self._table
