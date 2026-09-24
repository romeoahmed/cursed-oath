package io.github.romeoahmed.cursedoath.world

import jdk.jfr.Category
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.StackTrace

@Name("cursed_oath.Terrain")
@Label("Terrain excavation")
@Category("Cursed Oath")
@StackTrace(false)
internal class TerrainEvent : Event() {
    @JvmField var visits = 0

    @JvmField var blocks = 0

    @JvmField var pending = 0
}
