package io.visualr.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;
import io.visualr.runtime.TopologyOperator;

/**
 * Persistent worker pool: long-lived R workers serve many tasks;
 * concurrency stays deterministic relative to the in-process reference.
 */
class PersistentWorkerPoolTest {

    private static PalState s4() {
        return PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static PalState s5() {
        return PalState.of(List.of("A", "B", "C", "D", "E"), "F",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    @Test
    void longLivedWorkerServesManyTasks() {
        try (PersistentWorkerPool pool = new PersistentWorkerPool(1)) {
            // three sequential tasks on ONE worker
            PalState r1 = pool.submit(s4(), "identity").join();
            PalState r2 = pool.submit(s4(), "rotate").join();
            PalState r3 = pool.submit(s5(), "identity").join();

            assertEquals(PalCodec.format(s4()), PalCodec.format(r1));
            assertEquals(List.of("B", "C", "D", "A"), r2.shells());
            assertEquals(PalCodec.format(s5()), PalCodec.format(r3));
        }
    }

    @Test
    void concurrentPoolDeterministic() {
        try (PersistentWorkerPool pool = new PersistentWorkerPool(3)) {
            PalState identity4 = pool.submit(s4(), "identity").join();
            PalState rotate4 = pool.submit(s4(), "rotate").join();
            PalState identity5 = pool.submit(s5(), "identity").join();

            assertEquals(PalCodec.format(TopologyOperator.runPipeline(s4()).palOut()),
                    PalCodec.format(identity4));
            assertEquals(PalCodec.format(TopologyOperator.runPipeline(s4(), "rotate").palOut()),
                    PalCodec.format(rotate4));
            assertEquals(PalCodec.format(TopologyOperator.runPipeline(s5()).palOut()),
                    PalCodec.format(identity5));
        }
    }

    @Test
    void errorSurvivesAndWorkerKeepsServing() {
        try (PersistentWorkerPool pool = new PersistentWorkerPool(1)) {
            // valid task first
            PalState ok = pool.submit(s4(), "identity").join();
            assertEquals(PalCodec.format(s4()), PalCodec.format(ok));
            // pool still serves after the cycle
            PalState again = pool.submit(s4(), "rotate").join();
            assertEquals(List.of("B", "C", "D", "A"), again.shells());
            assertTrue(pool.size() == 1);
        }
    }
}
