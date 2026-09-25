package io.github.romeoahmed.cursedoath.network;

import java.util.UUID;

/// A connection-scoped, constant-space replay and rate limit. TCP preserves request order.
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
