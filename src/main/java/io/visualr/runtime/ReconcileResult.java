package io.visualr.runtime;

import java.util.List;

/**
 * Reconcile result — mirror of the R {@code reconcile} return list
 * {@code list(ok, conflicts, phase, action, reconciled_cell)}.
 *
 * <p>Action semantics: {@code promote} (accepted), {@code transient},
 * {@code recurse}, {@code reject} (fail-closed — non-closed states must
 * not enter canonical storage).</p>
 *
 * @param ok             logical — lanes agreed and state is determinable
 * @param conflicts      conflict descriptions (empty when ok)
 * @param phase          phase after transition
 * @param action         promote / transient / recurse / reject
 * @param reconciledCell the reconciled cell (original cell when rejected)
 */
public record ReconcileResult(boolean ok, List<String> conflicts, String phase,
                              String action, TopologyCell reconciledCell) {

    public ReconcileResult {
        conflicts = List.copyOf(conflicts);
        if (phase == null || action == null || reconciledCell == null) {
            throw new IllegalArgumentException("phase/action/reconciledCell must not be null");
        }
    }

    public static ReconcileResult rejected(List<String> conflicts, TopologyCell cell) {
        return new ReconcileResult(false, conflicts, cell.phase(), "reject", cell);
    }

    public static ReconcileResult promoted(String phase, TopologyCell cell) {
        return new ReconcileResult(true, List.of(), phase, "promote", cell);
    }
}
