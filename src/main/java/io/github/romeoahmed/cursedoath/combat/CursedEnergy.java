package io.github.romeoahmed.cursedoath.combat;

import org.jspecify.annotations.Nullable;

/// Integer units keep server-side energy reservations exact.
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

    public @Nullable CursedEnergy prepare(int startup, int release) {
        requireNonnegative(startup);
        requireNonnegative(release);
        return (long) startup + release > available() ? null : new CursedEnergy(current - startup, reserved + release);
    }

    public CursedEnergy release(int cost) {
        requireReserved(cost);
        return new CursedEnergy(current - cost, reserved - cost);
    }

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
