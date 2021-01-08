# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
#
# Licensed under the MIT-0 License. See the LICENSE accompanying this file
# for the specific language governing permissions and limitations under
# the License.

from aws_cdk import (
    aws_kinesis as kds,
    core
)
from aws_cdk.core import Duration


class KinesisStreamStack(core.Stack):

    def __init__(self, scope: core.Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        stream = kds.Stream(self, "InputStream",
                            shard_count=1,
                            retention_period=Duration.hours(24)
                            )

        self._stream = stream

    @property
    def stream(self) -> kds.IStream:
        return self._stream
