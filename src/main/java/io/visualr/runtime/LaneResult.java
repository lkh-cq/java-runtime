package io.visualr.runtime;

/**
 * Lane result — one lane's delta (mirror of R {@code lane_identity} output
 * {@code list(result, phase, action)}).
 *
 * @param endpoints the lane's result endpoint pair (or singularity token)
 * @param phase     phase label carried through the lane
 * @param action    action marker ("identity" for the default kernel)
 */
public record LaneResult(String[] endpoints, String phase, String action) {

    public LaneResult {
        if (endpoints == null) {
            throw new IllegalArgumentException("lane result endpoints must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("lane result action must not be null");
        }
        endpoints = endpoints.clone();
    }

    @Override
    public String[] endpoints() {
        return endpoints.clone();
    }

    public static LaneResult of(String[] endpoints, String phase, String action) {
        return new LaneResult(endpoints, phase, action);
    }
}
