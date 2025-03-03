/*
 * Copyright (c) 2022 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.elasticsearch;

import co.elastic.clients.elasticsearch._core.BulkResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airbyte.commons.concurrency.VoidCallable;
import io.airbyte.commons.functional.CheckedConsumer;
import io.airbyte.commons.functional.CheckedFunction;
import io.airbyte.integrations.base.AirbyteMessageConsumer;
import io.airbyte.integrations.destination.buffered_stream_consumer.BufferedStreamConsumer;
import io.airbyte.integrations.destination.buffered_stream_consumer.RecordWriter;
import io.airbyte.integrations.destination.record_buffer.InMemoryRecordBufferingStrategy;
import io.airbyte.protocol.models.AirbyteMessage;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.protocol.models.ConfiguredAirbyteCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchAirbyteMessageConsumerFactory {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchAirbyteMessageConsumerFactory.class);
  private static final int MAX_BATCH_SIZE_BYTES = 1024 * 1024 * 1024 / 4; // 256mib
  private static final ObjectMapper mapper = new ObjectMapper();

  private static final AtomicLong recordsWritten = new AtomicLong(0);

  /**
   * Holds a mapping of temp to target indices. After closing a sync job, the target index is removed
   * if it already exists, and the temp index is copied to replace it.
   */
  private static final Map<String, String> tempIndices = new HashMap<>();

  public static AirbyteMessageConsumer create(final Consumer<AirbyteMessage> outputRecordCollector,
                                              final ElasticsearchConnection connection,
                                              final List<ElasticsearchWriteConfig> writeConfigs,
                                              final ConfiguredAirbyteCatalog catalog) {

    return new BufferedStreamConsumer(
        outputRecordCollector,
        onStartFunction(connection, writeConfigs),
        new InMemoryRecordBufferingStrategy(recordWriterFunction(connection, writeConfigs), MAX_BATCH_SIZE_BYTES),
        onCloseFunction(connection),
        catalog,
        isValidFunction(connection));
  }

  // is there any json node that wont fit in the index?
  private static CheckedFunction<JsonNode, Boolean, Exception> isValidFunction(final ElasticsearchConnection connection) {
    return jsonNode -> true;
  }

  private static CheckedConsumer<Boolean, Exception> onCloseFunction(final ElasticsearchConnection connection) {

    return (hasFailed) -> {
      if (!tempIndices.isEmpty() && !hasFailed) {
        tempIndices.forEach(connection::replaceIndex);
      }
      connection.close();
    };
  }

  private static RecordWriter<AirbyteRecordMessage> recordWriterFunction(
                                                                         final ElasticsearchConnection connection,
                                                                         final List<ElasticsearchWriteConfig> writeConfigs) {

    return (pair, records) -> {
      log.info("writing {} records in bulk operation", records.size());
      final var optConfig = writeConfigs.stream()
          .filter(c -> Objects.equals(c.getStreamName(), pair.getName()) &&
              Objects.equals(c.getNamespace(), pair.getNamespace()))
          .findFirst();
      if (optConfig.isEmpty()) {
        throw new Exception(String.format("missing write config: %s", pair));
      }
      final var config = optConfig.get();
      final BulkResponse response;
      if (config.useTempIndex()) {
        response = connection.indexDocuments(config.getTempIndexName(), records, config);
      } else {
        response = connection.indexDocuments(config.getIndexName(), records, config);
      }
      if (Objects.nonNull(response) && response.errors()) {
        final String msg = String.format("failed to write bulk records: %s", mapper.valueToTree(response));
        throw new Exception(msg);
      } else {
        log.info("bulk write took: {}ms", response.took());
      }
    };
  }

  private static VoidCallable onStartFunction(final ElasticsearchConnection connection, final List<ElasticsearchWriteConfig> writeConfigs) {
    return () -> {
      for (final var config : writeConfigs) {
        if (config.useTempIndex()) {
          tempIndices.put(config.getTempIndexName(), config.getIndexName());
          connection.deleteIndexIfPresent(config.getTempIndexName());
          connection.createIndexIfMissing(config.getTempIndexName());
        } else {
          connection.createIndexIfMissing(config.getIndexName());
        }
      }
    };
  }

}
