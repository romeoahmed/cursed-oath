package io.github.romeoahmed.cursedoath.client.render.domain;

import com.google.gson.JsonParser;
import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/// The exporter bakes shading and rotation; resource reload rebuilds immutable faces.
final class ShrineModel {
    private final List<Face> faces;

    private record Face(List<Vec3> points, int color) {}

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
                var faces = new ArrayList<Face>();
                for (var entry :
                        JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("faces")) {
                    var face = entry.getAsJsonObject();
                    var points = new ArrayList<Vec3>();
                    for (var vertex : face.getAsJsonArray("vertices")) {
                        var xyz = vertex.getAsJsonArray();
                        points.add(new Vec3(
                                xyz.get(0).getAsDouble(),
                                xyz.get(1).getAsDouble(),
                                xyz.get(2).getAsDouble()));
                    }
                    if (points.size() != 4) throw new IllegalArgumentException("Shrine faces must be quads");
                    faces.add(new Face(List.copyOf(points), face.get("color").getAsInt() | 0xFF000000));
                }
                return new ShrineModel(faces);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load shrine model", exception);
        }
    }
}
