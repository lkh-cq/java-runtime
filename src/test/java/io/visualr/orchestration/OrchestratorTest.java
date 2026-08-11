package io.visualr.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;
import io.visualr.runtime.PipelineResult;
import io.visualr.runtime.TopologyOperator;

/**
 * Orchestration layer tests: Java schedules, R workers execute.
 * Results must be deterministic and agree with the in-process Java
 * pipeline (cross-validation against the R reference).
 */
class OrchestratorTest {

    private static PalState s4() {
        return PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static PalState s5() {
        return PalState.of(List.of("A", "B", "C", "D", "E"), "F",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    @Test
    void singleIdentityTask() {
        try (Orchestrator orch = new Orchestrator(2)) {
            PalState out = orch.submit(s4(), "identity").join();
            // identity pipeline: S_(t+1) PAL re-encodes to the same state
            assertEquals(PalCodec.format(s4()), PalCodec.format(out));
        }
    }

    @Test
    void rotateTaskMatchesInProcessPipeline() {
        try (Orchestrator orch = new Orchestrator(2)) {
            PalState out = orch.submit(s4(), "rotate").join();
            // cross-validate: R worker result == Java in-process pipeline result
            PipelineResult inProcess = TopologyOperator.runPipeline(s4(), "rotate");
            assertEquals(PalCodec.format(inProcess.palOut()), PalCodec.format(out));
            assertEquals(List.of("B", "C", "D", "A"), out.shells());
        }
    }

    @Test
    void concurrentBatchIsDeterministic() {
        try (Orchestrator orch = new Orchestrator(3)) {
            // identity batch over two different PALs, submitted concurrently
            List<PalState> identityOuts = orch.submitAll(List.of(s4(), s5()), "identity");
            // rotate task in parallel
            PalState rotateOut = orch.submit(s4(), "rotate").join();

            // identity pipeline: S_(t+1) PAL re-encodes to the same state
            assertEquals(PalCodec.format(s4()), PalCodec.format(identityOuts.get(0)));
            assertEquals(PalCodec.format(s5()), PalCodec.format(identityOuts.get(1)));
            // rotate advances A->B->C->D->A
            assertEquals(List.of("B", "C", "D", "A"), rotateOut.shells());

            // determinism contract: every batch result equals the in-process reference
            assertEquals(PalCodec.format(TopologyOperator.runPipeline(s4()).palOut()),
                    PalCodec.format(identityOuts.get(0)));
            assertEquals(PalCodec.format(TopologyOperator.runPipeline(s5()).palOut()),
                    PalCodec.format(identityOuts.get(1)));
            assertEquals(PalCodec.format(TopologyOperator.runPipeline(s4(), "rotate").palOut()),
                    PalCodec.format(rotateOut));
        }
    }
}
