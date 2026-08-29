package woo.siegePlugin.round;

import java.time.Instant;
import java.util.List;

/**
 * Restart-safe coordinator checkpoint. Nullable values are absent by phase.
 *
 * <p>{@code revision} is the compare-and-set token: a write only lands when the
 * stored row still carries the revision the caller read, which is what stops a
 * stale asynchronous completion from overwriting a newer lifecycle decision.</p>
 */
public record RotationState(
        RoundPhase phase,
        long generation,
        long revision,
        String currentMatchId,
        String currentMapId,
        String currentRuntimeWorld,
        String previousMapId,
        String preparedMapId,
        String preparedRuntimeWorld,
        Instant intermissionDeadline,
        List<RotationCandidate> candidates
) {
    public RotationState {
        candidates = List.copyOf(candidates);
    }

    /** The revision a successful write of this state will produce. */
    public RotationState nextRevision() {
        return new RotationState(
                phase, generation, revision + 1L, currentMatchId, currentMapId, currentRuntimeWorld,
                previousMapId, preparedMapId, preparedRuntimeWorld, intermissionDeadline, candidates
        );
    }

    public RotationState withPhase(RoundPhase next) {
        return new RotationState(
                next, generation, revision, currentMatchId, currentMapId, currentRuntimeWorld,
                previousMapId, preparedMapId, preparedRuntimeWorld, intermissionDeadline, candidates
        );
    }

    public RotationState withCandidates(List<RotationCandidate> next) {
        return new RotationState(
                phase, generation, revision, currentMatchId, currentMapId, currentRuntimeWorld,
                previousMapId, preparedMapId, preparedRuntimeWorld, intermissionDeadline, next
        );
    }

    /** Records the outcome of one preparation attempt without reordering the fallback list. */
    public RotationState withCandidateStatus(String mapId, RotationCandidate replacement) {
        return withCandidates(candidates.stream()
                .map(candidate -> candidate.mapId().equals(mapId) ? replacement : candidate)
                .toList());
    }

    public RotationState withPrepared(String preparedMapId, String preparedRuntimeWorld) {
        return new RotationState(
                phase, generation, revision, currentMatchId, currentMapId, currentRuntimeWorld,
                previousMapId, preparedMapId, preparedRuntimeWorld, intermissionDeadline, candidates
        );
    }
}
