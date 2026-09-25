package io.github.romeoahmed.cursedoath.combat

/** Integer units keep server-side energy reservations exact. */
data class CursedEnergy(
    val current: Int = CAPACITY,
    val reserved: Int = 0,
) {
    init {
        require(current in 0..CAPACITY && reserved in 0..current)
    }

    val available: Int get() = current - reserved

    fun prepare(
        startup: Int,
        release: Int,
    ): CursedEnergy? {
        require(startup >= 0 && release >= 0)
        if (startup.toLong() + release > available) return null
        return copy(current = current - startup, reserved = reserved + release)
    }

    fun release(cost: Int): CursedEnergy {
        require(cost in 0..reserved)
        return copy(current = current - cost, reserved = reserved - cost)
    }

    fun cancel(cost: Int): CursedEnergy {
        require(cost in 0..reserved)
        return copy(reserved = reserved - cost)
    }

    fun spend(cost: Int): CursedEnergy? {
        require(cost >= 0)
        return if (cost <= available) copy(current = current - cost) else null
    }

    fun recover(amount: Int): CursedEnergy {
        require(amount >= 0)
        return copy(current = (current.toLong() + amount).coerceAtMost(CAPACITY.toLong()).toInt())
    }

    companion object {
        const val CAPACITY = 1000
    }
}
