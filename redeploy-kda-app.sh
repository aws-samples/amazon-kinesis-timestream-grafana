#!/usr/bin/env bash

#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

app=${1:-java}
case $app in
  kotlin)  app_path="./analytics-kotlin/build/libs/analytics-timestream-kotlin-sample-all.jar"
  ;;
  \?) app="./analytics/target/analytics-timestream-java-sample-1.0.jar"
  ;;
esac
app_path="./analytics-kotlin/build/libs/analytics-timestream-kotlin-sample-all.jar"

wait_for_app_status () {
  n=0
  until [ "$n" -ge 20 ]
  do
     status=$(
              aws kinesisanalyticsv2 describe-application --application-name amazon-kinesis-analytics \
              | jq -r '.ApplicationDetail.ApplicationStatus'
              )
     if [ $status == $1 ]
     then
       echo "Application is now $1"
       break
     fi
     echo "Waiting for $1 status for analytics app, sleeping 15 seconds"
     n=$((n+1))
     sleep 15
  done
}

# Get Amazon Kinesis Data Analytics app description
kda_app_desc=$(aws kinesisanalyticsv2 describe-application --application-name amazon-kinesis-analytics)

# Determine S3 assets location
kda_code_path='.ApplicationDetail.ApplicationConfigurationDescription.ApplicationCodeConfigurationDescription.CodeContentDescription.S3ApplicationCodeLocationDescription'
s3_url_exp='('${kda_code_path}'.BucketARN+'\"/\"'+'${kda_code_path}'.FileKey)'

s3_url=$(echo ${kda_app_desc} | jq -r ${s3_url_exp})
s3_url='s3://'${s3_url:13}
aws s3 cp ${app_path} ${s3_url}
echo "Copied new application assets from ${app_path} to ${s3_url}"

# Stop Amazon Kinesis Data Analytics app
nohup aws kinesisanalyticsv2 stop-application --application-name amazon-kinesis-analytics \
  &>/dev/null &
echo "Stopping analytics app"
printf "\n"
wait_for_app_status 'READY'
echo "Stopped analytics app"

# Update Amazon Kinesis Data Analytics app
s3_key=$(
    echo ${kda_app_desc} \
    | jq '.ApplicationDetail.ApplicationConfigurationDescription.ApplicationCodeConfigurationDescription
    .CodeContentDescription.S3ApplicationCodeLocationDescription.FileKey'
)
nohup aws kinesisanalyticsv2 update-application --application-name amazon-kinesis-analytics \
  --application-configuration-update '{ "ApplicationCodeConfigurationUpdate": { "CodeContentUpdate": { "S3ContentLocationUpdate": { "FileKeyUpdate": ${s3_key} } } } }' \
  &>/dev/null &

wait_for_app_status 'READY'
echo "Updated analytics app"

# Run Amazon Kinesis Data Analytics app
echo "Starting analytics app"
nohup aws kinesisanalyticsv2 start-application --application-name amazon-kinesis-analytics \
  --run-configuration '{ "ApplicationRestoreConfiguration": { "ApplicationRestoreType": "SKIP_RESTORE_FROM_SNAPSHOT" } }' \
  &>/dev/null &
wait_for_app_status 'RUNNING'
echo "Started analytics app"
