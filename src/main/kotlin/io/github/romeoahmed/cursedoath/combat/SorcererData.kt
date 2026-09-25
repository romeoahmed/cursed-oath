package io.github.romeoahmed.cursedoath.combat

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.github.romeoahmed.cursedoath.CursedOath
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType

data class SorcererProfile(
    val version: Int = 1,
    val practice: Boolean = false,
    val reversal: Boolean = false,
    val selfHealing: Boolean = reversal,
    val barriers: Boolean = practice,
    val domainRadius: Double = 96.0,
) {
    companion object {
        val CODEC: Codec<SorcererProfile> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.intRange(1, 1).fieldOf("version").forGetter(SorcererProfile::version),
                        Codec.BOOL.fieldOf("practice").forGetter(SorcererProfile::practice),
                        Codec.BOOL.fieldOf("reversal").forGetter(SorcererProfile::reversal),
                        Codec.BOOL.optionalFieldOf("self_healing").forGetter { java.util.Optional.of(it.selfHealing) },
                        Codec.BOOL.optionalFieldOf("barriers").forGetter { java.util.Optional.of(it.barriers) },
                        Codec
                            .doubleRange(
                                16.0,
                                200.0,
                            ).optionalFieldOf("domain_radius", 96.0)
                            .forGetter(SorcererProfile::domainRadius),
                    ).apply(instance) { version, practice, reversal, healing, barriers, radius ->
                        SorcererProfile(
                            version,
                            practice,
                            reversal,
                            healing.orElse(reversal),
                            barriers.orElse(practice),
                            radius,
                        )
                    }
            }
    }
}

/** Balance, recovery, and burnout persist; active casts and reservations do not. */
data class SorcererResources(
    val energy: Int = CursedEnergy.CAPACITY,
    val recovery: Int = 0,
    val burnout: Int = 0,
) {
    companion object {
        val CODEC: Codec<SorcererResources> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.intRange(0, CursedEnergy.CAPACITY).fieldOf("energy").forGetter(SorcererResources::energy),
                        Codec.intRange(0, 1200).fieldOf("recovery").forGetter(SorcererResources::recovery),
                        Codec.intRange(0, 1200).optionalFieldOf("burnout", 0).forGetter(SorcererResources::burnout),
                    ).apply(instance, ::SorcererResources)
            }
    }
}

object SorcererData {
    val PROFILE: AttachmentType<SorcererProfile> =
        AttachmentRegistry.create(CursedOath.id("profile")) {
            it.initializer(::SorcererProfile).persistent(SorcererProfile.CODEC).copyOnDeath()
        }
    val RESOURCES: AttachmentType<SorcererResources> =
        AttachmentRegistry.create(CursedOath.id("resources")) {
            it.initializer(::SorcererResources).persistent(SorcererResources.CODEC).copyOnDeath()
        }

    fun initialize() = Unit
}
