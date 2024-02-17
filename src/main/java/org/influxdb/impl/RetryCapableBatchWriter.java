package org.influxdb.impl;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiConsumer;

/**
 * Batch writer that tries to retry a write if it failed previously and
 * the reason of the failure is not permanent.
 */
class RetryCapableBatchWriter implements BatchWriter {

  private InfluxDB influxDB;
  private BiConsumer<Iterable<Point>, Throwable> exceptionHandler;
  private LinkedList<BatchPoints> batchQueue;
  private int requestActionsLimit;
  private int retryBufferCapacity;
  private int usedRetryBufferCapacity;

  RetryCapableBatchWriter(final InfluxDB influxDB, final BiConsumer<Iterable<Point>, Throwable> exceptionHandler,
                          final int retryBufferCapacity, final int requestActionsLimit) {
    this.influxDB = influxDB;
    this.exceptionHandler = exceptionHandler;
    batchQueue = new LinkedList<>();
    this.retryBufferCapacity = retryBufferCapacity;
    this.requestActionsLimit = requestActionsLimit;
  }

  private enum WriteResultOutcome { WRITTEN, FAILED_RETRY_POSSIBLE, FAILED_RETRY_IMPOSSIBLE }

  private static final class WriteResult {

    static final WriteResult WRITTEN = new WriteResult(WriteResultOutcome.WRITTEN);

    WriteResultOutcome outcome;
    Throwable throwable;

    private WriteResult(final WriteResultOutcome outcome) {
      this.outcome = outcome;
    }

    private WriteResult(final WriteResultOutcome outcome, final Throwable throwable) {
      this.outcome = outcome;
      this.throwable = throwable;
    }

    private WriteResult(final InfluxDBException e) {
      this.throwable = e;
      if (e.isRetryWorth()) {
        this.outcome = WriteResultOutcome.FAILED_RETRY_POSSIBLE;
      } else {
        this.outcome = WriteResultOutcome.FAILED_RETRY_IMPOSSIBLE;
      }
    }
  }

  /* This method is synchronized to avoid parallel execution when the user invokes flush/close
   * of the client in the middle of scheduled write execution (buffer flush / action limit overrun) */
  @Override
  public synchronized void write(final Collection<BatchPoints> collection) {
    // empty the cached data first
    emptyCachedData(collection);
    // write the last given batch last so that duplicate data points get overwritten correctly
    writeLastBatch(collection);
  }

  private void emptyCachedData(final Collection<BatchPoints> collection){
    ListIterator<BatchPoints> batchQueueIterator = batchQueue.listIterator();
    while (batchQueueIterator.hasNext()) {
      BatchPoints entry = batchQueueIterator.next();
      WriteResult result = tryToWrite(entry);
      if (result.outcome == WriteResultOutcome.WRITTEN
              || result.outcome == WriteResultOutcome.FAILED_RETRY_IMPOSSIBLE) {
        batchQueueIterator.remove();
        usedRetryBufferCapacity -= entry.getPoints().size();
        // we are throwing out data, notify the client
        if (result.outcome == WriteResultOutcome.FAILED_RETRY_IMPOSSIBLE) {
          exceptionHandler.accept(entry.getPoints(), result.throwable);
        }
      } else {
        // we cannot send more data otherwise we would write them in different
        // order than in which were submitted
        for (BatchPoints batchPoints : collection) {
          addToBatchQueue(batchPoints);
        }
        return;
      }
    }
  }

  private void writeLastBatch(final Collection<BatchPoints> collection){
    Iterator<BatchPoints> collectionIterator = collection.iterator();
    while (collectionIterator.hasNext()) {
      BatchPoints batchPoints = collectionIterator.next();
      WriteResult result = tryToWrite(batchPoints);
      switch (result.outcome) {
        case FAILED_RETRY_POSSIBLE:
          addToBatchQueue(batchPoints);
          while (collectionIterator.hasNext()) {
            addToBatchQueue(collectionIterator.next());
          }
          break;
        case FAILED_RETRY_IMPOSSIBLE:
          exceptionHandler.accept(batchPoints.getPoints(), result.throwable);
          break;
        default:

      }
    }
  }

  /* This method is synchronized to avoid parallel execution when the BatchProcessor scheduler
   * has been shutdown but there are jobs still being executed (using RetryCapableBatchWriter.write).*/
  @Override
  public synchronized void close() {
    // try to write everything queued / buffered
    for (BatchPoints points : batchQueue) {
      WriteResult result = tryToWrite(points);
      if (result.outcome != WriteResultOutcome.WRITTEN) {
        exceptionHandler.accept(points.getPoints(), result.throwable);
      }
    }
  }

  private WriteResult tryToWrite(final BatchPoints batchPoints) {
    try {
      influxDB.write(batchPoints);
      return WriteResult.WRITTEN;
    } catch (InfluxDBException e) {
      return new WriteResult(e);
    } catch (Exception e) {
      return new WriteResult(WriteResultOutcome.FAILED_RETRY_POSSIBLE, e);
    }
  }

  private void evictTooOldFailedWrites() {
    while (usedRetryBufferCapacity > retryBufferCapacity && batchQueue.size() > 0) {
      List<Point> points = batchQueue.removeFirst().getPoints();
      usedRetryBufferCapacity -= points.size();
      exceptionHandler.accept(points,
              new InfluxDBException.RetryBufferOverrunException(
                      "Retry buffer overrun, current capacity: " + retryBufferCapacity));
    }
  }

  private void addToBatchQueue(final BatchPoints batchPoints) {
    boolean hasBeenMergedIn = false;
    if (batchQueue.size() > 0) {
      BatchPoints last = batchQueue.getLast();
      if (last.getPoints().size() + batchPoints.getPoints().size() <= requestActionsLimit) {
        hasBeenMergedIn = last.mergeIn(batchPoints);
      }
    }
    if (!hasBeenMergedIn) {
        batchQueue.add(batchPoints);
    }
    // recalculate local counter and evict old batches on merge as well
    usedRetryBufferCapacity += batchPoints.getPoints().size();
    evictTooOldFailedWrites();
  }
}
