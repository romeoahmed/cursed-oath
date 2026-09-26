package io.github.romeoahmed.cursedoath.network;

import java.util.UUID;

/// Connection-scoped replay protection and rate limiting for ordered requests.
public final class RequestGate {
    private static final int MAX_REQUESTS_PER_TICK = 4;
    private final UUID session;
    private long lastSequence = -1;
    private long windowTick = Long.MIN_VALUE;
    private int requests;

    public RequestGate() {
        this(UUID.randomUUID());
    }

    public RequestGate(UUID session) {
        this.session = session;
    }

    public UUID session() {
        return session;
    }

    /// Checks the session and sequence before applying the per-tick limit.
    /// A valid new sequence is consumed even when rate-limited, so it cannot be retried next tick.
    ///
    /// @param session connection token issued by the server
    /// @param sequence nonnegative, strictly increasing request number
    /// @param tick current server game tick
    /// @return whether the request may execute
    public boolean accept(UUID session, long sequence, long tick) {
        if (!session.equals(this.session) || sequence < 0 || sequence <= lastSequence) return false;
        lastSequence = sequence;
        if (windowTick != tick) {
            windowTick = tick;
            requests = 0;
        }
        return ++requests <= MAX_REQUESTS_PER_TICK;
    }
}
