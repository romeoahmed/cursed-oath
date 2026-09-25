package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.CursedOath
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.minecraft.world.level.Level

/** Each level owns its transient index; client and integrated server never share mutable sets. */
object DomainIndex {
    private val INDEX =
        AttachmentRegistry.create<MutableSet<DomainEntity>>(CursedOath.id("domains")) {
            it.initializer { linkedSetOf() }
        }
    private val server = linkedSetOf<DomainEntity>()
    val all: List<DomainEntity> get() = server.toList()

    fun add(domain: DomainEntity) {
        domain.level().getAttachedOrCreate(INDEX).add(domain)
        if (!domain.level().isClientSide) server.add(domain)
    }

    fun remove(domain: DomainEntity) {
        domain.level().getAttached(INDEX)?.remove(domain)
        if (!domain.level().isClientSide) server.remove(domain)
    }

    fun inLevel(level: Level): Collection<DomainEntity> = level.getAttached(INDEX).orEmpty()
}
