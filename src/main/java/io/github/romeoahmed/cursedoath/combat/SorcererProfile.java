package io.github.romeoahmed.cursedoath.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

public record SorcererProfile(
        int version, boolean practice, boolean reversal, boolean selfHealing, boolean barriers, double domainRadius) {
    public SorcererProfile() {
        this(1, false, false, false, false, 96.0);
    }

    public static SorcererProfile practiceProfile() {
        return new SorcererProfile(1, true, true, true, true, 96.0);
    }

    public SorcererProfile withDomainRadius(double radius) {
        return new SorcererProfile(version, practice, reversal, selfHealing, barriers, radius);
    }

    public static final Codec<SorcererProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(1, 1).fieldOf("version").forGetter(SorcererProfile::version),
                    Codec.BOOL.fieldOf("practice").forGetter(SorcererProfile::practice),
                    Codec.BOOL.fieldOf("reversal").forGetter(SorcererProfile::reversal),
                    Codec.BOOL.optionalFieldOf("self_healing").forGetter(value -> Optional.of(value.selfHealing)),
                    Codec.BOOL.optionalFieldOf("barriers").forGetter(value -> Optional.of(value.barriers)),
                    Codec.doubleRange(16.0, 200.0)
                            .optionalFieldOf("domain_radius", 96.0)
                            .forGetter(SorcererProfile::domainRadius))
            .apply(
                    instance,
                    (version, practice, reversal, healing, barriers, radius) -> new SorcererProfile(
                            version, practice, reversal, healing.orElse(reversal), barriers.orElse(practice), radius)));
}
