// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.services.timestream;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWrite;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClientBuilder;
import com.amazonaws.services.timestreamwrite.model.*;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Sink function for Flink to ingest data to Timestream
 */
public class TimestreamSink extends RichSinkFunction<Collection<TimestreamPoint>> implements CheckpointedFunction {
    private static final long RECORDS_FLUSH_INTERVAL_MILLISECONDS = 60L * 1000L; // One minute
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String region;
    private final String db;
    private final String table;
    private final Integer batchSize;
    private final BlockingQueue<Record> bufferedRecords;
    private transient ListState<Record> checkPointedState;
    private transient AmazonTimestreamWrite writeClient;
    private long emptyListTimestamp;

    public TimestreamSink(String region, String databaseName, String tableName, int batchSize) {
        this.region = region;
        this.db = databaseName;
        this.table = tableName;
        this.batchSize = batchSize;
        this.bufferedRecords = new LinkedBlockingQueue<>();
        this.emptyListTimestamp = System.currentTimeMillis();
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        final ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withMaxConnections(5000)
                .withRequestTimeout(20 * 1000)
                .withMaxErrorRetry(10);

        this.writeClient = AmazonTimestreamWriteClientBuilder
                .standard()
                .withRegion(this.region)
                .withClientConfiguration(clientConfiguration)
                .build();
    }

    @Override
    public void invoke(Collection<TimestreamPoint> points, Context context) {
        bufferedRecords.addAll(createRecords(points));

        if (shouldPublish()) {
            while (!bufferedRecords.isEmpty()) {
                List<Record> recordsToSend = new ArrayList<>(batchSize);
                bufferedRecords.drainTo(recordsToSend, batchSize);

                writeBatch(recordsToSend);
            }
        }
    }

    private void writeBatch(List<Record> recordsToSend) {
        WriteRecordsRequest writeRecordsRequest = new WriteRecordsRequest()
                .withDatabaseName(this.db)
                .withTableName(this.table)
                .withRecords(recordsToSend);

        try {
            WriteRecordsResult writeRecordsResult = this.writeClient.writeRecords(writeRecordsRequest);
            logger.debug("writeRecords Status: " + writeRecordsResult.getSdkHttpMetadata().getHttpStatusCode());
            emptyListTimestamp = System.currentTimeMillis();

        } catch (RejectedRecordsException e) {
            List<RejectedRecord> rejectedRecords = e.getRejectedRecords();
            logger.warn("Rejected Records -> " + rejectedRecords.size());
            for (int i = rejectedRecords.size() - 1; i >= 0; i--) {
                logger.warn("Discarding Malformed Record -> {}", rejectedRecords.get(i).toString());
                logger.warn("Rejected Record Reason -> {}", rejectedRecords.get(i).getReason());
            }
        } catch (Exception e) {
            logger.error("Error: " + e);
        }
    }

    private Collection<Record> createRecords(Collection<TimestreamPoint> points) {
        return points.stream()
                .map(point -> new Record()
                        .withDimensions(point.getDimensions().entrySet().stream()
                                .map(entry -> new Dimension()
                                        .withName(entry.getKey())
                                        .withValue(entry.getValue()))
                                .collect(Collectors.toList()))
                        .withMeasureName(point.getMeasureName())
                        .withMeasureValueType(point.getMeasureValueType())
                        .withMeasureValue(point.getMeasureValue())
                        .withTimeUnit(point.getTimeUnit())
                        .withTime(String.valueOf(point.getTime())))
                .collect(Collectors.toList());
    }

    // Method to validate if record batch should be published.
    // This method would return true if the accumulated records has reached the batch size.
    // Or if records have been accumulated for last RECORDS_FLUSH_INTERVAL_MILLISECONDS time interval.
    private boolean shouldPublish() {
        if (bufferedRecords.size() >= batchSize) {
            logger.debug("Batch of size " + bufferedRecords.size() + " should get published");
            return true;
        } else if (System.currentTimeMillis() - emptyListTimestamp >= RECORDS_FLUSH_INTERVAL_MILLISECONDS) {
            logger.debug("Records after flush interval should get published");
            return true;
        }
        return false;
    }

    @Override
    public void close() throws Exception {
        super.close();
    }

    @Override
    public void snapshotState(FunctionSnapshotContext functionSnapshotContext) throws Exception {
        checkPointedState.clear();
        for (Record bufferedRecord : bufferedRecords) {
            checkPointedState.add(bufferedRecord);
        }
    }

    @Override
    public void initializeState(FunctionInitializationContext functionInitializationContext) throws Exception {
        ListStateDescriptor<Record> descriptor = new ListStateDescriptor<>("recordList", Record.class);

        checkPointedState = functionInitializationContext.getOperatorStateStore().getListState(descriptor);

        if (functionInitializationContext.isRestored()) {
            for (Record element : checkPointedState.get()) {
                bufferedRecords.add(element);
            }
        }
    }
}