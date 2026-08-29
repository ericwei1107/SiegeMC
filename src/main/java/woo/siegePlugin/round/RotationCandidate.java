package woo.siegePlugin.round;

import java.util.Objects;

/**
 * One entry of the durable fallback order, with the outcome of the last attempt
 * to prepare it. An operator inspecting {@code /siege admin rotation status}
 * after a failed rollover needs to see which templates were already ruled out
 * and why.
 */
public record RotationCandidate(String mapId, CandidateStatus status, String failureReason) {

    /** Keeps one operator-facing reason readable and free of stack-trace noise. */
    private static final int MAX_REASON_LENGTH = 200;

    public RotationCandidate {
        Objects.requireNonNull(mapId, "mapId");
        if (mapId.isBlank()) {
            throw new IllegalArgumentException("mapId cannot be blank");
        }
        status = Objects.requireNonNull(status, "status");
        failureReason = sanitize(failureReason);
    }

    public static RotationCandidate pending(String mapId) {
        return new RotationCandidate(mapId, CandidateStatus.PENDING, null);
    }

    public RotationCandidate prepared() {
        return new RotationCandidate(mapId, CandidateStatus.PREPARED, null);
    }

    public RotationCandidate failed(String reason) {
        return new RotationCandidate(mapId, CandidateStatus.FAILED, reason);
    }

    public String describe() {
        return failureReason == null
                ? mapId + "(" + status + ")"
                : mapId + "(" + status + ": " + failureReason + ")";
    }

    /**
     * Failure reasons come from exception messages and validator output, so they
     * are collapsed to one bounded single-line string before being stored or
     * shown to an operator.
     */
    private static String sanitize(String reason) {
        if (reason == null) {
            return null;
        }
        String collapsed = reason.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() <= MAX_REASON_LENGTH
                ? collapsed
                : collapsed.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }

    public enum CandidateStatus {
        /** Not attempted yet during this intermission. */
        PENDING,
        /** Copy, load, or bind failed; do not retry automatically. */
        FAILED,
        /** Copied, loaded, and awaiting activation. */
        PREPARED
    }
}
