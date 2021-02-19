<!-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved. SPDX-License-Identifier: MIT-0 -->

# Sample Producer Lambda

## Configuration

The lambda function will read configuration defined in `config.py` and interpret it as following

|Configuration|Description|
|:------------|:----------|
|device_ids|List of devices that will produce reading records for|
|measures|List of measures to produce values for, every measure would nead to provide the following <br/>- measure: the measure name, examples (humidity, voltage, ...etc.) <br/>- start and end: 2 double values that the function will use to generate a uniformly distributed random value in this range. All measuments will contain only double values for this sample|
|iterations| Number of times to rpeat the process of generating values for every device whenever the function is called|
|chance_of_anomaly| Chance producer would intorduce a data point outside the range of start and end for a measure. Chance is uniform across all devices and number of iterations|

## Execution

Main producer function expects a `JSON` payload that contains the Amazon Kinesis Data stream name to send events to.

```json
{
  "Stream": "TestKinesisDataStreamName"
}
```

Once it runs it picks up measurements configurations and constructs an event to be sent to the data stream name as
follows.

1. Loops over device ids and constructs a record skeleton
   ```json
   {
      "Time": "current_time",
      "DeviceID": "device_id"
   }
   ```
2. Loops over measures
3. Add fields to the record as follows
    ```json
    {
      "measures.measure + _measure": "random(measures.measure.start, measures.measure.end)"
    }
    ```

Producing records similar to bellow example and adding them to the Kinesis data stream.

```json
{
  "Time": 1609757175225.499,
  "DeviceID": "1aeb6e58-9d5b-4fd6-a5c3-6f7dd09a150d",
  "temperature_measure": 15.5,
  "humidity_measure": 70.3,
  "voltage_measure": 39.7,
  "watt_measure": 301.4
}
```

## Schedule

The lambda function is scheduled to be called by an Amazon EventBridge rule invoked at a 1 minuite rate. 