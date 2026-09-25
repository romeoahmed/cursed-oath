package io.github.romeoahmed.cursedoath.world;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("cursed_oath.Terrain")
@Label("Terrain excavation")
@Category("Cursed Oath")
@StackTrace(false)
final class TerrainEvent extends Event {
    public int visits;
    public int blocks;
    public int pending;
}
