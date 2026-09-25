package io.github.romeoahmed.cursedoath.client.render.domain

import com.google.gson.JsonParser
import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/** The exporter bakes rotations and directional shading; resource reload rebuilds immutable faces. */
internal class ShrineModel private constructor(
    private val faces: List<Face>,
) {
    private data class Face(
        val points: List<Vec3>,
        val color: Int,
    )

    fun draw(mesh: EffectMesh) {
        for ((points, color) in faces) mesh.quad(points[0], points[1], points[2], points.last(), color)
    }

    companion object {
        fun load(): ShrineModel {
            val resource =
                Minecraft.getInstance().resourceManager.getResourceOrThrow(CursedOath.id("models/domain/shrine.json"))
            val faces =
                resource.openAsReader().use { reader ->
                    JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("faces").map { entry ->
                        val face = entry.asJsonObject
                        val points =
                            face.getAsJsonArray("vertices").map { vertex ->
                                val xyz = vertex.asJsonArray
                                Vec3(xyz[0].asDouble, xyz[1].asDouble, xyz[2].asDouble)
                            }
                        require(points.size == 4) { "Shrine faces must be quads" }
                        Face(points, face["color"].asInt or 0xFF000000.toInt())
                    }
                }
            return ShrineModel(faces)
        }
    }
}
