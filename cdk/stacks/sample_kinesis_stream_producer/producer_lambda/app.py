# Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

import json
import random
import time

import boto3
from config import device_ids, measures, iterations, chance_of_anomaly

kinesis = boto3.client('kinesis')


def handler(event, context):
    sent = 0
    for x in range(iterations):
        records = []
        for device_id in device_ids:
            records.append(prepare_record(measures, device_id,
                                          (iterations - x) / iterations * 60))

        print("records {}".format(len(records)))
        write_records(event["Stream"], records)
        sent = sent + len(records)
    return {"records": sent}


def prepare_record(some_measures, device_id, delta_seconds):
    current_time = int((time.time() - delta_seconds) * 1000)
    record = {
        'Time': current_time,
        'DeviceID': device_id,
    }

    for measure_field in some_measures:
        measure_value = random.uniform(measure_field['start'], measure_field['end'])
        if random.random() < chance_of_anomaly:
            if random.random() > 0.5:
                measure_value = measure_value + measure_field['end']
            else:
                measure_value = measure_value - measure_field['start']
        record[measure_field['measure'] + '_measure'] = measure_value

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
