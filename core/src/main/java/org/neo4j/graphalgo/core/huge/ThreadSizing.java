package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.core.utils.paged.BitUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

final class ThreadSizing {

    // batch size is used to presize multiple arrays
    // 2B elements might even be too much as arrays need to be allocated with
    // a consecutive chunk of memory
    // possible idea: retry with lower batch sizes if alloc hits an OOM?
    private static final long MAX_BATCH_SIZE = 2_000_000_000L;

    private static final String NOT_ENOUGH_THREADS_AVAILABLE =
            "There are only %d threads available and with %d nodes this would mean that "
                    + "every thread would have to process %d nodes each, which is too large and unsupported.";

    private static final String TOO_MANY_THREADS_REQUIRED =
            "Importing %d nodes would need %d threads which cannot be created.";

    private final int targetThreads;
    private final int batchSize;

    ThreadSizing(int concurrency, long nodeCount, ExecutorService executor) {
        // we need at least that many threads, probably more
        long threadLowerBound = nodeCount / MAX_BATCH_SIZE;

        // start with the desired level of concurrency
        long targetThreads = (long) concurrency;

        // we batch by shifting on the node id, so the batchSize must be a power of 2
        long batchSize = BitUtil.nextHighestPowerOfTwo(ceilDiv(nodeCount, targetThreads));

        // increase thread size until we have a small enough batch size
        while (batchSize > MAX_BATCH_SIZE) {
            targetThreads = Math.max(threadLowerBound, 1L + targetThreads);
            batchSize = BitUtil.nextHighestPowerOfTwo(ceilDiv(nodeCount, targetThreads));
        }

        // we need to run all threads at the same time and not have them queued
        // or else we risk a deadlock where the scanner waits on an importer thread to finish
        // but that importer thread is queued and hasn't even started
        // If we have another pool, we just have to hope for the best (or, maybe throw?)
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
            // TPE only increases to max threads if the queue is full, (see their JavaDoc)
            // but by that point it's already too late for us, so we have to fit every thread in the core pool
            long availableThreads = (long) (pool.getCorePoolSize() - pool.getActiveCount());
            if (availableThreads < targetThreads) {
                targetThreads = availableThreads;
                batchSize = BitUtil.nextHighestPowerOfTwo(ceilDiv(nodeCount, targetThreads));
                if (batchSize > MAX_BATCH_SIZE) {
                    throw new IllegalArgumentException(
                            String.format(NOT_ENOUGH_THREADS_AVAILABLE, availableThreads, nodeCount, batchSize)
                    );
                }
            }
        }

        if (targetThreads > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    String.format(TOO_MANY_THREADS_REQUIRED, nodeCount, targetThreads)
            );
        }

        // int casts are safe as both are < MAX_BATCH_SIZE
        this.targetThreads = (int) targetThreads;
        this.batchSize = (int) batchSize;
    }

    int numberOfThreads() {
        return targetThreads;
    }

    int batchSize() {
        return batchSize;
    }

    private static long ceilDiv(long dividend, long divisor) {
        return 1L + (-1L + dividend) / divisor;
    }
}
