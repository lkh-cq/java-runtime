package io.visualr.orchestration;

import io.visualr.runtime.PalState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persistent worker pool — fixed set of long-lived R workers, round-robin
 * task dispatch (DEVELOPMENT_PLAN §8: Java schedules, R workers execute).
 *
 * <p>Each worker serves one task at a time; the pool provides concurrency.
 * A dead worker is replaced transparently before retry (single retry).</p>
 */
public final class PersistentWorkerPool implements AutoCloseable {

    private final List<PersistentRWorker> workers;
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final ExecutorService pool;

    /** Create a pool with {@code size} long-lived R workers. */
    public PersistentWorkerPool(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        workers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            try {
                workers.add(new PersistentRWorker());
            } catch (IOException ex) {
                throw new IllegalStateException("failed to start R worker " + i + ": " + ex.getMessage(), ex);
            }
        }
        pool = Executors.newFixedThreadPool(size);
    }

    /** Submit one task; the worker pool executes it concurrently. */
    public CompletableFuture<PalState> submit(PalState pal, String kernelName) {
        return CompletableFuture.supplyAsync(() -> dispatch(pal, kernelName), pool);
    }

    private PalState dispatch(PalState pal, String kernelName) {
        PersistentRWorker worker = nextWorker();
        try {
            return worker.runTask(pal, kernelName);
        } catch (IllegalStateException ex) {
            // transparent single retry on a fresh worker (dead-process recovery)
            PersistentRWorker replacement = replace(worker);
            return replacement.runTask(pal, kernelName);
        }
    }

    private synchronized PersistentRWorker nextWorker() {
        int idx = Math.floorMod(cursor.getAndIncrement(), workers.size());
        PersistentRWorker w = workers.get(idx);
        if (!w.isAlive()) {
            workers.set(idx, newWorker());
            return workers.get(idx);
        }
        return w;
    }

    private synchronized PersistentRWorker replace(PersistentRWorker dead) {
        int idx = workers.indexOf(dead);
        if (idx < 0) {
            throw new IllegalStateException("worker not in pool");
        }
        dead.forceDestroy(); // gate review P2-4: never leak a stuck/dead R process
        workers.set(idx, newWorker());
        return workers.get(idx);
    }

    private PersistentRWorker newWorker() {
        try {
            return new PersistentRWorker();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to start replacement R worker: " + ex.getMessage(), ex);
        }
    }

    public int size() { return workers.size(); }

    @Override
    public void close() {
        pool.shutdown();
        for (PersistentRWorker w : workers) {
            w.close();
        }
    }
}
