package io.github.romeoahmed.cursedoath.combat;

import org.jspecify.annotations.Nullable;

/// Immutable energy balance; reserved units remain in the balance but cannot be spent twice.
///
/// @param current balance, including reservations, between zero and [#CAPACITY]
/// @param reserved committed release costs, between zero and `current`
public record CursedEnergy(int current, int reserved) {
    public static final int CAPACITY = 1000;

    public CursedEnergy {
        if (current < 0 || current > CAPACITY || reserved < 0 || reserved > current)
            throw new IllegalArgumentException("Invalid energy balance");
    }

    public CursedEnergy() {
        this(CAPACITY, 0);
    }

    public CursedEnergy(int current) {
        this(current, 0);
    }

    public int available() {
        return current - reserved;
    }

    /// Pays the startup cost and reserves the release cost atomically.
    ///
    /// @param startup nonnegative cost paid immediately
    /// @param release nonnegative cost held until release or cancellation
    /// @return updated balance, or `null` if available energy cannot cover both costs
    /// @throws IllegalArgumentException if either cost is negative
    public @Nullable CursedEnergy prepare(int startup, int release) {
        requireNonnegative(startup);
        requireNonnegative(release);
        return (long) startup + release > available() ? null : new CursedEnergy(current - startup, reserved + release);
    }

    public CursedEnergy release(int cost) {
        requireReserved(cost);
        return new CursedEnergy(current - cost, reserved - cost);
    }

    /// Frees a release reservation without refunding the startup cost.
    ///
    /// @param cost reserved amount to free
    /// @return balance with that reservation removed
    /// @throws IllegalArgumentException if the cost is negative or exceeds the reservation
    public CursedEnergy cancel(int cost) {
        requireReserved(cost);
        return new CursedEnergy(current, reserved - cost);
    }

    public @Nullable CursedEnergy spend(int cost) {
        requireNonnegative(cost);
        return cost <= available() ? new CursedEnergy(current - cost, reserved) : null;
    }

    public CursedEnergy recover(int amount) {
        requireNonnegative(amount);
        return amount == 0 || current == CAPACITY
                ? this
                : new CursedEnergy((int) Math.min((long) current + amount, CAPACITY), reserved);
    }

    private void requireReserved(int cost) {
        if (cost < 0 || cost > reserved) throw new IllegalArgumentException("Cost exceeds reservation");
    }

    private static void requireNonnegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy amount");
    }
}
