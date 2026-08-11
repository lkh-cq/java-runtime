package io.visualr.orchestration;

import io.visualr.runtime.PalState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrator — long-lived Java/JVM scheduling layer (DEVELOPMENT_PLAN
 * §8 post-0.5 role). Java schedules, R workers execute:
 *
 * <pre>
 * Java/JVM scheduler
 *         |
 *         +--&gt; R worker
 *         +--&gt; R worker
 *         +--&gt; R worker
 * </pre>
 *
 * <p>Determinism contract (DEVELOPMENT_PLAN §6): concurrent results
 * equal single-worker results — the orchestrator only parallelizes
 * independent PAL pipelines, never shares mutable state.</p>
 */
public final class Orchestrator implements AutoCloseable {

    private final ExecutorService pool;
    private final int maxWorkers;

    /** Create an orchestrator with up to {@code maxWorkers} concurrent R workers. */
    public Orchestrator(int maxWorkers) {
        if (maxWorkers < 1) {
            throw new IllegalArgumentException("maxWorkers must be >= 1");
        }
        this.maxWorkers = maxWorkers;
        this.pool = Executors.newFixedThreadPool(maxWorkers);
    }

    /** Submit one pipeline task; returns the re-encoded S_(t+1) PAL. */
    public CompletableFuture<PalState> submit(PalState pal, String kernelName) {
        return CompletableFuture.supplyAsync(() -> RWorker.runTask(pal, kernelName), pool);
    }

    /**
     * Submit many tasks and await all results in submission order.
     * Concurrent execution must be deterministic relative to the
     * reference: results are gathered in input order regardless of
     * completion order.
     */
    public List<PalState> submitAll(List<PalState> pals, String kernelName) {
        List<CompletableFuture<PalState>> futures = new ArrayList<>(pals.size());
        for (PalState pal : pals) {
            futures.add(submit(pal, kernelName));
        }
        List<PalState> results = new ArrayList<>(pals.size());
        for (CompletableFuture<PalState> f : futures) {
            results.add(f.join());
        }
        return results;
    }

    public int maxWorkers() { return maxWorkers; }

    @Override
    public void close() {
        pool.shutdown();
    }
}
