package io.github.romeoahmed.cursedoath.client.render.domain;

import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import net.minecraft.world.phys.Vec3;

/// Broken, opposing edges locate overlap without filling the contested volume.
final class DomainClash {
    record Neighbor(Vec3 center, double radius, boolean drawSeam) {
        boolean crosses(Vec3 start, Vec3 end) {
            var motion = end.subtract(start);
            double t = Math.clamp(center.subtract(start).dot(motion) / motion.lengthSqr(), 0, 1);
            return center.distanceToSqr(start.lerp(end, t)) < radius * radius;
        }
    }

    private DomainClash() {}

    static void draw(EffectMesh mesh, double radius, Neighbor other, float age, Vec3 eye, boolean subdued) {
        if (!other.drawSeam()) return;
        double distance = other.center().length();
        Vec3 normal = distance < 0.001 ? new Vec3(0, 1, 0) : other.center().scale(1 / distance);
        double depth = distance < 0.001
                ? 0
                : (radius * radius - other.radius() * other.radius() + distance * distance) / (2 * distance);
        boolean nested = distance <= Math.abs(radius - other.radius());
        double ringRadius =
                nested ? Math.min(radius, other.radius()) : Math.sqrt(Math.max(0, radius * radius - depth * depth));
        Vec3 center = nested ? (radius <= other.radius() ? Vec3.ZERO : other.center()) : normal.scale(depth);
        var axes = EffectMesh.basis(normal);
        if (!nested) {
            fractures(mesh, Vec3.ZERO, radius, normal, depth, age, subdued);
            fractures(mesh, other.center(), other.radius(), normal.scale(-1), distance - depth, age, subdued);
        }
        for (int i = 0; i < 64; i++) {
            if ((i + (int) (age / 6)) % 4 == 0) continue;
            double a = i * Math.TAU / 64, b = (i + 0.78) * Math.TAU / 64;
            var start = center.add(axes.side().scale(Math.cos(a) * ringRadius))
                    .add(axes.up().scale(Math.sin(a) * ringRadius));
            var end = center.add(axes.side().scale(Math.cos(b) * ringRadius))
                    .add(axes.up().scale(Math.sin(b) * ringRadius));
            float alpha = (float) Math.clamp((eye.distanceTo(start) - 2) / 4, 0, 1) * (subdued ? 0.3f : 0.65f);
            var width = normal.scale(0.12);
            mesh.slash(start, end, width, 0x120C16, alpha);
            mesh.ribbon(start, end, width.scale(-0.4), i % 2 == 0 ? 0xCEB4B0 : 0x798FAE, alpha);
        }
    }

    private static void fractures(
            EffectMesh mesh, Vec3 center, double radius, Vec3 normal, double depth, float age, boolean subdued) {
        var axes = EffectMesh.basis(normal);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.TAU / 24;
            double d = Math.clamp(depth - 1.5 - (i % 3) * 0.5, -radius * 0.95, radius * 0.95);
            double r = Math.sqrt(radius * radius - d * d);
            var radial = axes.side().scale(Math.cos(angle)).add(axes.up().scale(Math.sin(angle)));
            var start = center.add(normal.scale(d)).add(radial.scale(r + 0.06));
            double endDepth = Math.min(depth - 0.12, d + 1.8);
            double endRadius = Math.sqrt(Math.max(0, radius * radius - endDepth * endDepth));
            var end = center.add(normal.scale(endDepth)).add(radial.scale(endRadius + 0.06));
            var width = axes.side()
                    .scale(-Math.sin(angle))
                    .add(axes.up().scale(Math.cos(angle)))
                    .scale(0.09);
            float pulse = 0.6f + 0.15f * (float) Math.sin(age * 0.17 + i);
            mesh.slash(start, end, width, 0xB9AFC9, subdued ? pulse * 0.3f : pulse);
        }
    }
}
