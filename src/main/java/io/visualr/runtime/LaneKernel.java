package io.visualr.runtime;

import java.util.Map;

/**
 * Lane kernel — the local operator applied to ONE orbit (or the
 * singularity) at the SAME logical instant (ABI v0.1 §5.3).
 *
 * <p>Java mirror of the R lane-kernel ABI {@code (orbit, phase, pack)}
 * (see {@code execute_lanes_ops} / {@code lane_kernels}): the default
 * identity kernel returns the orbit unchanged; concrete kernels plug in
 * later. Lanes are pure functions over one snapshot — no shared state
 * writes.</p>
 *
 * @param endpoints the orbit endpoint pair (or the singularity token)
 * @param phase     current phase label
 * @param pack      resolved mapping pack (null when unavailable — Java
 *                  pack resolution is a later slice)
 */
@FunctionalInterface
public interface LaneKernel {

    LaneResult apply(String[] endpoints, String phase, Map<String, Object> pack);

    /**
     * Identity kernel (mirror of R {@code lane_identity}):
     * {@code result = endpoints}, phase unchanged, {@code action = "identity"}.
     */
    LaneKernel IDENTITY = (endpoints, phase, pack) -> LaneResult.of(endpoints, phase, "identity");
}
