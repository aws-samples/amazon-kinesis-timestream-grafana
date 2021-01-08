// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package services.timestream

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWrite
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClientBuilder
import com.amazonaws.services.timestreamwrite.model.ConflictException
import com.amazonaws.services.timestreamwrite.model.CreateDatabaseRequest
import com.amazonaws.services.timestreamwrite.model.CreateTableRequest
import com.amazonaws.services.timestreamwrite.model.RetentionProperties
import org.slf4j.LoggerFactory

/**
 * Checks if required database and table exists in Timestream. If they do not exists, it creates them
 */
class TimestreamInitializer(region: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TimestreamInitializer::class.java)
        private const val HT_TTL_HOURS = 24L
        private const val CT_TTL_DAYS = 7L
    }

    private val writeClient: AmazonTimestreamWrite

    init {
        val clientConfiguration = ClientConfiguration()
            .withMaxConnections(5000)
            .withRequestTimeout(20 * 1000)
            .withMaxErrorRetry(10)
        writeClient = AmazonTimestreamWriteClientBuilder
            .standard()
            .withRegion(region)
            .withClientConfiguration(clientConfiguration)
            .build()
    }

    fun createDatabase(databaseName: String) {
        LOG.info("Creating database")
        val request = CreateDatabaseRequest()
        request.databaseName = databaseName
        try {
            writeClient.createDatabase(request)
            LOG.info("Database [$databaseName] created successfully")
        } catch (e: ConflictException) {
            LOG.info("Database [$databaseName] exists. Skipping database creation")
        }
    }

    fun createTable(databaseName: String, tableName: String) {
        LOG.info("Creating table")
        val createTableRequest = CreateTableRequest()
        createTableRequest.databaseName = databaseName
        createTableRequest.tableName = tableName
        val retentionProperties = RetentionProperties()
            .withMemoryStoreRetentionPeriodInHours(HT_TTL_HOURS)
            .withMagneticStoreRetentionPeriodInDays(CT_TTL_DAYS)
        createTableRequest.retentionProperties = retentionProperties
        try {
            writeClient.createTable(createTableRequest)
            LOG.info("Table [$tableName] successfully created.")
        } catch (e: ConflictException) {
            LOG.info("Table [$tableName] exists on database [$databaseName]. Skipping table creation")
        }
    }
}