package io.github.romeoahmed.cursedoath.client.render.domain;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/// The exporter bakes shading and rotation; resource reload rebuilds immutable faces.
final class ShrineModel {
    private final List<Face> faces;

    private record Face(List<Vec3> points, int color) {
        private static final Codec<Face> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Vec3.CODEC.listOf(4, 4).fieldOf("vertices").forGetter(Face::points),
                        Codec.INT.fieldOf("color").forGetter(Face::color))
                .apply(instance, Face::new));

        private Face {
            points = List.copyOf(points);
            color |= 0xFF000000;
        }
    }

    private static final Codec<ShrineModel> CODEC = Face.CODEC
            .listOf()
            .fieldOf("faces")
            .xmap(ShrineModel::new, model -> model.faces)
            .codec();

    private ShrineModel(List<Face> faces) {
        this.faces = List.copyOf(faces);
    }

    void draw(EffectMesh mesh) {
        for (var face : faces) {
            var points = face.points();
            mesh.quad(points.get(0), points.get(1), points.get(2), points.getLast(), face.color());
        }
    }

    static ShrineModel load() {
        try {
            var resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResourceOrThrow(CursedOath.id("models/domain/shrine.json"));
            try (var reader = resource.openAsReader()) {
                return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .getOrThrow(message -> new IllegalArgumentException("Invalid shrine model: " + message));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load shrine model", exception);
        }
    }
}
