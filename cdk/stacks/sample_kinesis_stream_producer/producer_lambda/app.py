# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

import boto3
import json
import random
import time

from config import device_ids, measures, iterations

kinesis = boto3.client('kinesis')


def handler(event, context):
    sent = 0
    for x in range(iterations):
        records = []
        for device_id in device_ids:
            records.append(prepare_record(measures, device_id))

        print("records {}".format(len(records)))
        write_records(event["Stream"], records)
        sent = sent + len(records)
    return {"records": sent}


def prepare_record(some_measures, device_id):
    current_time = int(time.time() * 1000)
    record = {
        'Time': current_time,
        'DeviceID': device_id,
    }

    for measure_field in some_measures:
        record[measure_field['measure'] + '_measure'] = \
            random.uniform(measure_field['start'], measure_field['end'])

    return record


def write_records(stream_name, records):
    kinesis_records = []
    for record in records:
        kinesis_records.append({
            'Data': json.dumps(record),
            'PartitionKey': record["DeviceID"]
        })

    result = None
    try:
        result = kinesis.put_records(
            StreamName=stream_name,
            Records=kinesis_records, )

        status = result['ResponseMetadata']['HTTPStatusCode']
        print("Processed %d records. WriteRecords Status: %s" %
              (len(records), status))
    except Exception as err:
        print("Error:", err)
        if result is not None:
            print("Result:{}".format(result))
