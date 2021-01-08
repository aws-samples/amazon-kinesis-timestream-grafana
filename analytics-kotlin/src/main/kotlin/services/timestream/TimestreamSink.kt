// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package services.timestream

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWrite
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClientBuilder
import com.amazonaws.services.timestreamwrite.model.Dimension
import com.amazonaws.services.timestreamwrite.model.Record
import com.amazonaws.services.timestreamwrite.model.RejectedRecordsException
import com.amazonaws.services.timestreamwrite.model.WriteRecordsRequest
import org.apache.flink.api.common.state.ListState
import org.apache.flink.api.common.state.ListStateDescriptor
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.FunctionInitializationContext
import org.apache.flink.runtime.state.FunctionSnapshotContext
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors

/**
 * Sink function for Flink to ingest data to Timestream
 */
class TimestreamSink(
    private val region: String, private val db: String, private val table: String,
    private val batchSize: Int
) :
    RichSinkFunction<Collection<TimestreamPoint>>(), CheckpointedFunction {
    companion object {
        private const val RECORDS_FLUSH_INTERVAL_MILLISECONDS = 60L * 1000L // One minute
        private val LOG = LoggerFactory.getLogger(TimestreamSink::class.java)
    }

    private val bufferedRecords = LinkedBlockingQueue<Record>()

    @Transient
    private lateinit var checkPointedState: ListState<Record>

    @Transient
    private lateinit var writeClient: AmazonTimestreamWrite

    private var emptyListTimestamp: Long = System.currentTimeMillis()

    override fun open(parameters: Configuration) {
        super.open(parameters)
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

    override fun invoke(value: Collection<TimestreamPoint>) {

        bufferedRecords.addAll(createRecords(value))

        if (shouldPublish()) {
            while (!bufferedRecords.isEmpty()) {
                val recordsToSend: MutableList<Record> = ArrayList(batchSize)
                bufferedRecords.drainTo(recordsToSend, batchSize)
                writeBatch(recordsToSend)
            }
        }
    }

    private fun writeBatch(recordsToSend: MutableList<Record>) {
        val writeRecordsRequest = WriteRecordsRequest()
            .withDatabaseName(db)
            .withTableName(table)
            .withRecords(recordsToSend)
        try {
            val writeRecordsResult = writeClient.writeRecords(writeRecordsRequest)
            LOG.debug("writeRecords Status: ${writeRecordsResult.sdkHttpMetadata.httpStatusCode}")
            emptyListTimestamp = System.currentTimeMillis()
        } catch (e: RejectedRecordsException) {
            val rejectedRecords = e.rejectedRecords
            LOG.warn("Rejected Records -> ${rejectedRecords.size}")
            rejectedRecords.forEach {
                LOG.warn("Discarding Malformed Record -> $it")
                LOG.warn("Rejected Record Reason -> ${it.reason}")
            }
        } catch (e: Exception) {
            LOG.error("Error: $e", e)
        }
    }

    private fun createRecords(points: Collection<TimestreamPoint>): Collection<Record> {
        return points.stream()
            .map {
                Record()
                    .withDimensions(
                        it.getDimensions().entries.stream()
                            .map { entry ->
                                Dimension().withName(entry.key).withValue(entry.value)
                            }.collect(Collectors.toList())
                    )
                    .withMeasureName(it.measureName)
                    .withMeasureValueType(it.measureValueType)
                    .withMeasureValue(it.measureValue)
                    .withTimeUnit(it.timeUnit)
                    .withTime(it.time.toString())
            }
            .collect(Collectors.toList())
    }

    // Method to validate if record batch should be published.
    // This method would return true if the accumulated records has reached the batch size.
    // Or if records have been accumulated for last RECORDS_FLUSH_INTERVAL_MILLISECONDS time interval.
    private fun shouldPublish(): Boolean {
        if (bufferedRecords.size >= batchSize) {
            LOG.debug("Batch of size ${bufferedRecords.size} should get published")
            return true
        } else if (System.currentTimeMillis() - emptyListTimestamp >= RECORDS_FLUSH_INTERVAL_MILLISECONDS) {
            LOG.debug("Records after flush interval should get published")
            return true
        }
        return false
    }

    override fun snapshotState(functionSnapshotContext: FunctionSnapshotContext) {
        checkPointedState.clear()
        bufferedRecords.forEach(checkPointedState::add)
    }

    override fun initializeState(functionInitializationContext: FunctionInitializationContext) {
        val descriptor = ListStateDescriptor("recordList", Record::class.java)
        checkPointedState = functionInitializationContext.operatorStateStore.getListState(descriptor)
        if (functionInitializationContext.isRestored) {
            bufferedRecords.addAll(checkPointedState.get())
        }
    }
}