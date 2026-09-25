package io.github.romeoahmed.cursedoath.technique;

import com.google.errorprone.annotations.Immutable;

@Immutable
public record CastCost(int startup, int release) {}
