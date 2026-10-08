// Steady View: copied from Minecraft 26.3 and modified for the see-through feature in third-person view (search for "Steady View")
#ifndef MINECRAFT_GLOBALS_GLSL
#define MINECRAFT_GLOBALS_GLSL

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
    // Steady View: see-through cutout for third-person view (see cutout.glsl).
    // xyz: player eye relative to camera, w: radius in blocks, 0 = off
    vec4 SteadyViewCutout;
    // Steady View: what can be seen from the original camera position in third-person view (see visibility.glsl).
    // xyz: world position of the grid corner, w: 1 = hide what cannot be seen, 0 = off (the rest is not written)
    ivec4 SteadyViewGridOrigin;
    // xyz: original camera position relative to the grid corner
    vec4 SteadyViewViewpoint;
    // xyz: player's eye relative to the grid corner, w: 1 = used
    vec4 SteadyViewEye;
    // Visibility of each cell, 1 bit per cell (48 * 48 * 48 bits)
    ivec4 SteadyViewGrid[864];
};

#endif
