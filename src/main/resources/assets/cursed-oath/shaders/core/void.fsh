#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    // Fade the panorama seam into the surrounding black space.
    float seam = smoothstep(0.0, 0.025, min(texCoord0.x, 1.0 - texCoord0.x));
    float reveal = smoothstep(0.0, 1.0, vertexColor.a);
    fragColor = vec4(texture(Sampler0, texCoord0).rgb * seam * reveal, 1.0);
}
