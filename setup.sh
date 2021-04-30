#!/usr/bin/env sh

#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Run Amazon Kinesis Data Analytics app
nohup aws kinesisanalyticsv2 start-application --application-name amazon-kinesis-analytics \
  --run-configuration '{ "ApplicationRestoreConfiguration": { "ApplicationRestoreType": "SKIP_RESTORE_FROM_SNAPSHOT" } }' \
  &>/dev/null &
echo "Started analytics app"
printf "\n"

# Get grafana URL and credentials
grafana_url=$(
  aws cloudformation describe-stacks --stack-name grafana \
    --query "Stacks[0].Outputs[?starts_with(OutputKey, 'MyFargateServiceServiceURL')].OutputValue" --output text
)

grafana_secret_name=$(
  aws cloudformation describe-stacks --stack-name grafana \
    --query "Stacks[0].Outputs[?OutputKey=='GrafanaAdminSecret'].OutputValue" --output text
)
grafana_secret_name_no_suffix=${grafana_secret_name%-*}
grafana_admin_password=$(
  aws secretsmanager get-secret-value --secret-id ${grafana_secret_name_no_suffix} \
    --query "SecretString" --output text
)

# Create grafana API token
grafana_token=$(
  curl -X POST -u "admin:${grafana_admin_password}" \
    -H "Content-Type: application/json" \
    -d '{"name":"apikeycurl", "role": "Admin"}' \
    "${grafana_url}"/api/auth/keys |
    jq -r .key
)
echo "Created Grafana API token ${grafana_token}"
printf "\n"

# Use grafana API to create data source and dashboard
curl -X POST -k \
  -H "Content-Type: application/json" -H "Authorization: Bearer ${grafana_token}" \
  -d @./cdk/stacks/grafana/datasource.json \
  "${grafana_url}"/api/datasources
printf "\n"

curl -X POST -k \
  -H "Content-Type: application/json" -H "Authorization: Bearer ${grafana_token}" \
  -d @./cdk/stacks/grafana/dashboard.json \
  "${grafana_url}"/api/dashboards/db
printf "\n"
echo "Now you can head to ${grafana_url} to check newly created dashboard."
echo "Using credentials admin:${grafana_admin_password}"
printf "\n"
