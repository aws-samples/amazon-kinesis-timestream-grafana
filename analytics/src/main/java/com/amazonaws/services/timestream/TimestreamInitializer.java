// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.services.timestream;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWrite;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClientBuilder;
import com.amazonaws.services.timestreamwrite.model.ConflictException;
import com.amazonaws.services.timestreamwrite.model.CreateDatabaseRequest;
import com.amazonaws.services.timestreamwrite.model.CreateTableRequest;
import com.amazonaws.services.timestreamwrite.model.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks if required database and table exists in Timestream. If they do not exists, it creates them
 */
public class TimestreamInitializer {
    private static final long HT_TTL_HOURS = 24L;
    private static final long CT_TTL_DAYS = 7L;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private AmazonTimestreamWrite writeClient;

    public TimestreamInitializer(String region) {
        final ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withMaxConnections(5000)
                .withRequestTimeout(20 * 1000)
                .withMaxErrorRetry(10);

        this.writeClient = AmazonTimestreamWriteClientBuilder
                .standard()
                .withRegion(region)
                .withClientConfiguration(clientConfiguration)
                .build();
    }

    public void createDatabase(String databaseName) {
        logger.info("Creating database");
        CreateDatabaseRequest request = new CreateDatabaseRequest();
        request.setDatabaseName(databaseName);
        try {
            writeClient.createDatabase(request);
            logger.info("Database [" + databaseName + "] created successfully");
        } catch (ConflictException e) {
            logger.info("Database [" + databaseName + "] exists. Skipping database creation");
        }
    }

    public void createTable(String databaseName, String tableName) {
        logger.info("Creating table");
        CreateTableRequest createTableRequest = new CreateTableRequest();
        createTableRequest.setDatabaseName(databaseName);
        createTableRequest.setTableName(tableName);
        final RetentionProperties retentionProperties = new RetentionProperties()
                .withMemoryStoreRetentionPeriodInHours(HT_TTL_HOURS)
                .withMagneticStoreRetentionPeriodInDays(CT_TTL_DAYS);
        createTableRequest.setRetentionProperties(retentionProperties);

        try {
            writeClient.createTable(createTableRequest);
            logger.info("Table [" + tableName + "] successfully created.");
        } catch (ConflictException e) {
            logger.info("Table [" + tableName + "] exists on database [" + databaseName + "]. Skipping table creation");
        }
    }
}
