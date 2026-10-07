// Steady View: copied from Minecraft 26.3 and modified for the see-through cutout (search for "Steady View")
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
    // Steady View: see-through cutout for third-person view (xyz: player eye relative to camera, w: radius in blocks, 0 = off)
    vec4 SteadyViewCutout;
};

#endif
