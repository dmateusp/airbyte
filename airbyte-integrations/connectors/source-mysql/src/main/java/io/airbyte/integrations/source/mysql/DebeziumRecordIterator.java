/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.integrations.source.mysql;

import com.google.common.collect.AbstractIterator;
import io.airbyte.commons.concurrency.VoidCallable;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.lang.MoreBooleans;
import io.airbyte.commons.util.AutoCloseableIterator;
import io.debezium.engine.ChangeEvent;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The record iterator is the consumer (in the producer / consumer relationship with debezium)
 * responsible for 1. making sure every record produced by the record publisher is processed 2.
 * signalling to the record publisher when it is time for it to stop producing records. It emits
 * this signal either when the publisher had not produced a new record for a long time or when it
 * has processed at least all of the records that were present in the database when the source was
 * started. Because the publisher might publish more records between the consumer sending this
 * signal and the publisher actually shutting down, the consumer must stay alive as long as the
 * publisher is not closed. Even after the publisher is closed, the consumer will finish processing
 * any produced records before closing.
 */
public class DebeziumRecordIterator extends AbstractIterator<ChangeEvent<String, String>>
    implements AutoCloseableIterator<ChangeEvent<String, String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DebeziumRecordIterator.class);

  private static final TimeUnit SLEEP_TIME_UNIT = TimeUnit.SECONDS;
  private static final int SLEEP_TIME_AMOUNT = 5;

  private final LinkedBlockingQueue<ChangeEvent<String, String>> queue;
  private final Optional<TargetFilePosition> targetFilePosition;
  private final Supplier<Boolean> publisherStatusSupplier;
  private final VoidCallable requestClose;

  public DebeziumRecordIterator(LinkedBlockingQueue<ChangeEvent<String, String>> queue,
                                Optional<TargetFilePosition> targetFilePosition,
                                Supplier<Boolean> publisherStatusSupplier,
                                VoidCallable requestClose) {
    this.queue = queue;
    this.targetFilePosition = targetFilePosition;
    this.publisherStatusSupplier = publisherStatusSupplier;
    this.requestClose = requestClose;
  }

  @Override
  protected ChangeEvent<String, String> computeNext() {
    // keep trying until the publisher is closed or until the queue is empty. the latter case is
    // possible when the publisher has shutdown but the consumer has not yet processed all messages it
    // emitted.
    while (!MoreBooleans.isTruthy(publisherStatusSupplier.get()) || !queue.isEmpty()) {
      final ChangeEvent<String, String> next;
      try {
        next = queue.poll(SLEEP_TIME_AMOUNT, SLEEP_TIME_UNIT);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // if within the timeout, the consumer could not get a record, it is time to tell the producer to
      // shutdown.
      if (next == null) {
        requestClose();
        LOGGER.info("no record found. polling again.");
        continue;
      }

      // if the last record matches the target file position, it is time to tell the producer to shutdown.
      if (shouldSignalClose(next)) {
        requestClose();
      }

      return next;
    }
    return endOfData();
  }

  @Override
  public void close() throws Exception {
    requestClose.call();
  }

  private boolean shouldSignalClose(ChangeEvent<String, String> event) {
    if (targetFilePosition.isEmpty()) {
      return false;
    }

    String file = Jsons.deserialize(event.value()).get("source").get("file").asText();
    int position = Jsons.deserialize(event.value()).get("source").get("pos").asInt();
    if (!file.equals(targetFilePosition.get().fileName)) {
      return false;
    }

    if (targetFilePosition.get().position >= position) {
      return false;
    }

    // if not snapshot or is snapshot but last record in snapshot.
    return SnapshotMetadata.TRUE != SnapshotMetadata.valueOf(
        Jsons.deserialize(event.value()).get("source").get("snapshot").asText()
            .toUpperCase());
  }

  private void requestClose() {
    try {
      requestClose.call();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  enum SnapshotMetadata {
    TRUE,
    FALSE,
    LAST
  }

}
