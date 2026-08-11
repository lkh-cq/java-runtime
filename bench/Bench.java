import io.visualr.orchestration.PersistentRWorker;
import io.visualr.orchestration.RWorker;
import io.visualr.runtime.PalState;

import java.util.List;
import java.util.Map;

/**
 * One-off benchmark: fresh-process RWorker vs persistent PersistentRWorker.
 * Run: javac -cp target/classes bench/Bench.java && java -cp target/classes:bench Bench
 */
public class Bench {
    public static void main(String[] args) throws Exception {
        PalState pal = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());

        int n = 5;
        // warm both once
        RWorker.runTask(pal, "identity");
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            RWorker.runTask(pal, "rotate");
        }
        long freshMs = (System.nanoTime() - t0) / 1_000_000;

        try (PersistentRWorker w = new PersistentRWorker()) {
            w.runTask(pal, "identity"); // warm
            long t1 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                w.runTask(pal, "rotate");
            }
            long persistMs = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("fresh-process:   " + (freshMs / (double) n) + " ms/task (n=" + n + ")");
            System.out.println("persistent:      " + (persistMs / (double) n) + " ms/task (n=" + n + ")");
            System.out.printf("speedup:         %.1fx%n", freshMs / (double) Math.max(persistMs, 1));
        }
    }
}
