package woo.siegePlugin.round;


import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic read boundary shared by gameplay services during context swaps. */
public final class ActiveRoundProvider implements RoundActivityStatus {

    private final AtomicReference<State> state = new AtomicReference<>(new State(RoundPhase.BOOTSTRAPPING, null));

    public Optional<ActiveRoundContext> current() {
        return Optional.ofNullable(state.get().context());
    }

    public RoundPhase phase() {
        return state.get().phase();
    }

    public boolean publish(RoundPhase expectedPhase, long generation, ActiveRoundContext context) {
        while (true) {
            State before = state.get();
            if (before.phase() != expectedPhase || (before.context() != null
                    && generation <= before.context().generation())) {
                return false;
            }
            if (state.compareAndSet(before, new State(RoundPhase.ACTIVE, context))) {
                return true;
            }
        }
    }

    public boolean transition(RoundPhase expected, RoundPhase next) {
        while (true) {
            State before = state.get();
            if (before.phase() != expected) {
                return false;
            }
            ActiveRoundContext context = next == RoundPhase.ACTIVE ? before.context() : before.context();
            if (state.compareAndSet(before, new State(next, context))) {
                return true;
            }
        }
    }

    public void restore(RoundPhase phase, ActiveRoundContext context) {
        state.set(new State(phase, context));
    }

    @Override
    public boolean isActive() {
        return phase() == RoundPhase.ACTIVE;
    }

    private record State(RoundPhase phase, ActiveRoundContext context) {
    }
}
