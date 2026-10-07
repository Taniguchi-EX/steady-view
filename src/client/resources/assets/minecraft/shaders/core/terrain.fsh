#version 330
// Steady View: copied from Minecraft 26.3 and modified for the see-through cutout (search for "Steady View")
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 cameraRelativePos;

// Steady View: skip faces near the line from the camera to the player's eye and in front of the player.
// Back faces are already culled, so skipping the camera-facing face makes the block see-through.
// The region is a truncated cone (radius SteadyViewCutout.w at the camera, half of it near the player).
// Its edge is dithered so that it fades out gradually.
const float STEADYVIEW_KEEP_BEFORE_EYE = 0.7;
const float STEADYVIEW_EDGE = 0.4;

float steadyViewDither() {
    // 4x4 Bayer matrix (0 to 1)
    int x = int(mod(gl_FragCoord.x, 4.0));
    int y = int(mod(gl_FragCoord.y, 4.0));
    const float bayer[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (bayer[x + y * 4] + 0.5) / 16.0;
}

bool steadyViewCutout() {
    float radius = SteadyViewCutout.w;
    if (radius <= 0.0) {
        return false;
    }

    vec3 toEye = SteadyViewCutout.xyz;
    float length2 = dot(toEye, toEye);
    float eyeDistance = sqrt(length2);
    // Position along the line (0: camera, 1: eye). Stop a little before the eye
    float t = dot(cameraRelativePos, toEye) / length2;
    float stop = 1.0 - STEADYVIEW_KEEP_BEFORE_EYE / eyeDistance;
    if (t <= 0.0 || t >= stop) {
        return false;
    }

    float distanceFromLine = length(cameraRelativePos - toEye * t);
    float localRadius = radius * mix(1.0, 0.5, t / stop);
    if (distanceFromLine < localRadius) {
        return true;
    }

    float edge = (distanceFromLine - localRadius) / STEADYVIEW_EDGE;
    return edge < 1.0 && edge < steadyViewDither();
}

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    if (steadyViewCutout()) {
        discard;
    }

    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
